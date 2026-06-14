# weight-control

## Project setup
```
yarn install
```

### Compiles and hot-reloads for development
```
yarn serve
```

### Compiles and minifies for production
```
yarn build
```

### Lints and fixes files
```
yarn lint
```

### Import MariaDB data
```bash
./scripts/import-db.sh
```

This script:
- removes the Docker volumes
- rebuilds and starts the stack
- waits until the backend finishes importing `backups/current`
- prints the imported row counts

### Google login
Set both `GOOGLE_CLIENT_ID` and `VUE_APP_GOOGLE_CLIENT_ID` in `.env` to the same Google Web client ID before rebuilding the stack.

### Bootstrap infrastructure with Ansible
Copy the example inventory and set your server IP:

```bash
cp infra/ansible/inventory.ini.example infra/ansible/inventory.ini
```

Install the required Ansible collections:

```bash
./scripts/setup-ansible.sh
source .venv-ansible/bin/activate
```

Fill `infra/ansible/group_vars/all.yml` with your SSH public key in `admin_ssh_public_keys`, then run:

```bash
ansible-playbook -i infra/ansible/inventory.ini infra/ansible/playbook.yml
```

If your system Ansible is old, use the virtualenv Ansible from `.venv-ansible`. This repo pins a newer `ansible-core` for modern Ubuntu targets.

This bootstraps the server only:
- OS updates and base packages
- Docker Engine and Docker Compose plugin
- `deploy` sudo user
- SSH hardening
- UFW and fail2ban
- base directories under `/opt`

### Deploy application with Ansible
Set the production values in `infra/ansible/group_vars/all.yml`, especially:
- `app_domains`
- `app_allowed_origins`
- `app_db_password`
- `app_db_root_password`
- `app_jwt_secret`
- `app_google_client_id`

Then deploy with:

```bash
source .venv-ansible/bin/activate
ansible-playbook -i infra/ansible/inventory.ini infra/ansible/deploy-app.yml
```

This playbook:
- syncs the repo to the server
- renders `.env`
- renders `Caddyfile`
- runs `docker compose up -d --build`

### Customize configuration
See [Configuration Reference](https://cli.vuejs.org/config/).
