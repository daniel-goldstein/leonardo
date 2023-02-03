#!/usr/bin/env bash
set -e

# If you update this file, please update azure.custom-script-extension.file-uris in reference.conf so that Leonardo can adopt the new script
# This is to avoid the error Ref BioC
# 'debconf: unable to initialize frontend: Dialog'
export DEBIAN_FRONTEND=noninteractive

#create user to run jupyter
VM_JUP_USER=jupyter

sudo useradd -m -c "Jupyter User" $VM_JUP_USER
sudo usermod -a -G $VM_JUP_USER,adm,dialout,cdrom,floppy,audio,dip,video,plugdev,lxd,netdev $VM_JUP_USER

## Change ownership for the new user

sudo chgrp $VM_JUP_USER /anaconda/bin/*

sudo chown $VM_JUP_USER /anaconda/bin/*

sudo chgrp $VM_JUP_USER /anaconda/envs/py38_default/bin/*

sudo chown $VM_JUP_USER /anaconda/envs/py38_default/bin/*

sudo systemctl disable --now jupyterhub.service

# Read script arguments
echo $# arguments
if [$# -ne 13];
    then echo "illegal number of parameters"
fi

RELAY_NAME=$1
RELAY_CONNECTION_NAME=$2
RELAY_TARGET_HOST=$3
RELAY_CONNECTION_POLICY_KEY=$4
LISTENER_DOCKER_IMAGE=$5
SAMURL=$6
SAMRESOURCEID=$7
CONTENTSECURITYPOLICY_FILE=$8

# Envs for welder
WELDER_WSM_URL=${9:-localhost}
WELDER_WORKSPACE_ID="${10:-dummy}"
WELDER_STORAGE_CONTAINER_RESOURCE_ID="${11:-dummy}"
WELDER_WELDER_DOCKER_IMAGE="${12:-dummy}"
WELDER_OWNER_EMAIL="${13:-dummy}"
WELDER_STAGING_BUCKET="${14:-dummy}"
WELDER_STAGING_STORAGE_CONTAINER_RESOURCE_ID="${15:-dummy}"

# Jupyter variables for listener
SERVER_APP_BASE_URL="/${RELAY_CONNECTION_NAME}/"
SERVER_APP_ALLOW_ORIGIN="*"
HCVAR='\$hc'
SERVER_APP_WEBSOCKET_URL="wss://${RELAY_NAME}.servicebus.windows.net/${HCVAR}/${RELAY_CONNECTION_NAME}"

#Relay listener configuration
RELAY_CONNECTIONSTRING="Endpoint=sb://${RELAY_NAME}.servicebus.windows.net/;SharedAccessKeyName=listener;SharedAccessKey=${RELAY_CONNECTION_POLICY_KEY};EntityPath=${RELAY_CONNECTION_NAME}"

# Install relevant libraries

/anaconda/envs/py38_default/bin/pip3 install igv-jupyter

/anaconda/envs/py38_default/bin/pip3 install seaborn

#Update rbase version
echo "Y"|sudo apt install --no-install-recommends r-base

#Update kernel list

echo "Y"| /anaconda/bin/jupyter kernelspec remove sparkkernel

echo "Y"| /anaconda/bin/jupyter kernelspec remove sparkrkernel

echo "Y"| /anaconda/bin/jupyter kernelspec remove pysparkkernel  

echo "Y"| /anaconda/bin/jupyter kernelspec remove spark-3-python 

echo "Y"| /anaconda/bin/jupyter kernelspec remove julia-1.6

/anaconda/bin/jupyter kernelspec install /anaconda/envs/py38_default/share/jupyter/kernels/python3

# Start Jupyter server with custom parameters
sudo runuser -l $VM_JUP_USER -c "mkdir -p /home/$VM_JUP_USER/.jupyter"
sudo runuser -l $VM_JUP_USER -c "wget -qP /home/$VM_JUP_USER/.jupyter https://raw.githubusercontent.com/DataBiosphere/leonardo/710389b23b6d6ad6e5698632fe5c0eb34ea952e2/http/src/main/resources/init-resources/jupyter_server_config.py"
sudo runuser -l $VM_JUP_USER -c "wget -qP /anaconda/lib/python3.9/site-packages https://raw.githubusercontent.com/DataBiosphere/terra-docker/622ce501c10968aae26fdf5f5223bda3ffcba3a3/terra-jupyter-base/custom/jupyter_delocalize.py"
sudo runuser -l $VM_JUP_USER -c "sed -i 's/http:\/\/welder:8080/http:\/\/127.0.0.1:8081/g' /anaconda/lib/python3.9/site-packages/jupyter_delocalize.py"
sudo runuser -l $VM_JUP_USER -c "/anaconda/bin/jupyter server --ServerApp.base_url=$SERVER_APP_BASE_URL --ServerApp.websocket_url=$SERVER_APP_WEBSOCKET_URL --ServerApp.contents_manager_class=jupyter_delocalize.WelderContentsManager --autoreload &> /home/$VM_JUP_USER/jupyter.log" >/dev/null 2>&1&

# Store Jupyter Server parameters for reboot processls
sudo crontab -l 2>/dev/null| cat - <(echo "@reboot sudo runuser -l $VM_JUP_USER -c '/anaconda/bin/jupyter server --ServerApp.base_url=$SERVER_APP_BASE_URL --ServerApp.websocket_url=$SERVER_APP_WEBSOCKET_URL --ServerApp.contents_manager_class=jupyter_delocalize.WelderContentsManager --autoreload &> /home/$VM_JUP_USER/jupyter.log' >/dev/null 2>&1&") | crontab -

#Run docker container with Relay Listener
docker run -d --restart always --network host --name listener \
--env LISTENER_RELAYCONNECTIONSTRING=$RELAY_CONNECTIONSTRING \
--env LISTENER_RELAYCONNECTIONNAME=$RELAY_CONNECTION_NAME \
--env LISTENER_SAMINSPECTORPROPERTIES_SAMRESOURCEID=$SAMRESOURCEID \
--env LISTENER_SAMINSPECTORPROPERTIES_SAMURL=$SAMURL \
--env LISTENER_CORSSUPPORTPROPERTIES_CONTENTSECURITYPOLICY="$(cat $CONTENTSECURITYPOLICY_FILE)" \
--env LISTENER_TARGETPROPERTIES_TARGETHOST="http://${RELAY_TARGET_HOST}:8888" \
--env LISTENER_TARGETPROPERTIES_TARGETROUTINGRULES_0_PATHCONTAINS="welder" \
--env LISTENER_TARGETPROPERTIES_TARGETROUTINGRULES_0_TARGETHOST="http://${RELAY_TARGET_HOST}:8081" \
--env LISTENER_TARGETPROPERTIES_TARGETROUTINGRULES_0_REMOVEFROMPATH="\$hc-name/welder" \
--env LOGGING_LEVEL_ROOT=INFO \
$LISTENER_DOCKER_IMAGE

docker run -d --restart always --network host --name welder \
--volume "/home/${VM_JUP_USER}":"/work" \
--env WSM_URL=$WELDER_WSM_URL \
--env PORT=8081 \
--env WORKSPACE_ID=$WELDER_WORKSPACE_ID \
--env STORAGE_CONTAINER_RESOURCE_ID=$WELDER_STORAGE_CONTAINER_RESOURCE_ID \
--env STAGING_STORAGE_CONTAINER_RESOURCE_ID=$WELDER_STAGING_STORAGE_CONTAINER_RESOURCE_ID \
--env OWNER_EMAIL=$WELDER_OWNER_EMAIL \
--env CLOUD_PROVIDER="azure" \
--env LOCKING_ENABLED=false \
--env STAGING_BUCKET=$WELDER_STAGING_BUCKET \
--env SHOULD_BACKGROUND_SYNC="false" \
$WELDER_WELDER_DOCKER_IMAGE
