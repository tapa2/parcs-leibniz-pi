#!/bin/bash
# Delete the lab VMs so they stop using the trial credit.
ZONE="${ZONE:-europe-central2-a}"
DAEMONS="${DAEMONS:-daemon-1 daemon-2 daemon-3}"
gcloud compute instances delete app $DAEMONS --zone "$ZONE" --quiet
gcloud compute instances list
