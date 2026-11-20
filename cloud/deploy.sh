#!/bin/bash
# Deploy the PARCS cluster to Google Compute Engine and run the lab.
#
# Run from Google Cloud Shell, inside the unpacked lab folder:
#     bash cloud/deploy.sh
#
# Layout (fits the 8 vCPU quota of a new trial project):
#     app       - hosts-server (port 3333) + root module Main     e2-standard-2
#     daemon-N  - PARCS daemon (port 2222), runs the workers      e2-standard-2
# Safe to rerun: existing VMs are reused, old processes are restarted.
set -euo pipefail

ZONE="${ZONE:-europe-central2-a}"
TYPE="${TYPE:-e2-standard-2}"
DAEMONS="${DAEMONS:-daemon-1 daemon-2 daemon-3}"

PROJECT="$(gcloud config get-value project 2>/dev/null)"
if [ -z "$PROJECT" ]; then
    echo "No project selected. Run: gcloud config set project <PROJECT_ID>"
    exit 1
fi
echo "== project $PROJECT, zone $ZONE"
gcloud services enable compute.googleapis.com

# 1. Create the VMs that do not exist yet
for vm in app $DAEMONS; do
    if gcloud compute instances describe "$vm" --zone "$ZONE" >/dev/null 2>&1; then
        echo "== $vm already exists"
    else
        gcloud compute instances create "$vm" --zone "$ZONE" --machine-type "$TYPE" \
            --image-family debian-12 --image-project debian-cloud
    fi
done

internal_ip() {
    gcloud compute instances describe "$1" --zone "$ZONE" \
        --format='get(networkInterfaces[0].networkIP)'
}

# ssh with retries: right after creation a VM may not accept ssh yet
run() {
    local vm="$1" cmd="$2"
    for attempt in 1 2 3 4 5 6; do
        if gcloud compute ssh "$vm" --zone "$ZONE" --quiet --command "$cmd"; then
            return 0
        fi
        echo "== ssh to $vm failed (attempt $attempt), retrying in 10 s"
        sleep 10
    done
    return 1
}

copy() {  # copy <vm> <dest> <files...>
    local vm="$1" dest="$2"
    shift 2
    gcloud compute scp --recurse --zone "$ZONE" --quiet "$@" "$vm:$dest"
}

INSTALL='sudo apt-get -o DPkg::Lock::Timeout=180 update -qq && sudo DEBIAN_FRONTEND=noninteractive apt-get -o DPkg::Lock::Timeout=180 install -y -qq openjdk-17-jdk-headless make >/dev/null'
# Stop the previous process by its pid file (pkill -f would kill this ssh shell too)
restart() {  # restart <pidfile> <logfile> <command>
    echo "{ [ -f $1 ] && kill \$(cat $1) 2>/dev/null; true; }; nohup $3 > $2 2>&1 < /dev/null & echo \$! > $1"
}

# 2. Daemons
DAEMON_IPS=""
for d in $DAEMONS; do
    echo "== setting up $d"
    run "$d" "$INSTALL"
    copy "$d" "~/" cloud/Daemon.jar
    run "$d" "$(restart daemon.pid daemon.log 'java -jar Daemon.jar')"
    DAEMON_IPS="$DAEMON_IPS $(internal_ip "$d")"
done
APP_IP="$(internal_ip app)"

# 3. App VM: hosts-server + the lab itself
echo "== setting up app ($APP_IP), daemons:$DAEMON_IPS"
run app "$INSTALL && mkdir -p ~/hs ~/lab/out"
copy app "~/hs/" cloud/TCPHostsServer.jar
copy app "~/lab/" src Makefile
copy app "~/lab/out/" out/parcs.jar
copy app "~/lab/out/input" out/input
run app "cd ~/hs && printf '%s\n' $DAEMON_IPS > hosts.list && cat hosts.list && $(restart hs.pid hs.log 'java -jar TCPHostsServer.jar')"
run app "echo $APP_IP > ~/lab/out/server && rm -rf ~/lab/build ~/lab/out/Main.jar ~/lab/out/SeriesWorker.jar"

# 4. Run the lab
sleep 3
echo "== cluster:"
gcloud compute instances list --format='table(name,machineType.basename(),networkInterfaces[0].networkIP,status)'
echo "== running the lab on app"
run app "cd ~/lab && cat out/input && make run"
echo "== done. Delete the VMs when finished: bash cloud/cleanup.sh"
