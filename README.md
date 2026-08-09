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

### Merge master into the current worktree branch
Update `master` in its worktree, then merge it into the branch checked out in the current worktree:

```bash
git -C ../weight-control pull --ff-only origin master
git merge master
```

The second command includes commits that exist only in local `master`, even when there are no pending remote changes.

### Clone production DB into localhost
Dump production over SSH:

```bash
./scripts/dump-prod-db.sh deploy@weightcontrol.devjllado.com
```

This reads the remote `.env` from `/opt/weight-control`, runs `mariadb-dump` inside the production `mariadb` container, and stores a compressed dump under `tmp/`.

Restore the dump locally:

```bash
./scripts/restore-local-db.sh tmp/prod-2026-06-14-120000.sql.gz
```

This script:
- removes the local Docker volumes
- starts only `mariadb`
- restores the SQL dump
- starts the stack
- prints the imported row counts

### Google login
Set both `GOOGLE_CLIENT_ID` and `VUE_APP_GOOGLE_CLIENT_ID` in `.env` to the same Google Web client ID before rebuilding the stack.

### Server prerequisite

Before deploying, provide a reachable server with SSH access for the `deploy` user, passwordless sudo, Docker Engine, and the Docker Compose plugin.

### Deploy application with Ansible
Copy the example inventory and set the persistent server address if the local inventory does not exist:

```bash
cp infra/ansible/inventory.ini.example infra/ansible/inventory.ini
```

Install the deployment dependencies:

```bash
./scripts/setup-ansible.sh
source .venv-ansible/bin/activate
```

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

The production defaults use shared-gateway mode.
The app-local Caddy does not bind public host ports and joins the `shared_edge` Docker network as `weight-control-caddy`.

### Direct public exposure mode
If you want to expose `weight-control` directly with its own Caddy ports, deploy with:

```bash
ansible-playbook -i infra/ansible/inventory.ini infra/ansible/deploy-app.yml \
  -e '{"app_compose_files":["docker-compose.yml","docker-compose.override.yml"],"app_caddy_site_address":"weightcontrol.devjllado.com","app_caddy_http_port":80,"app_caddy_https_port":443}'
```

### Customize configuration
See [Configuration Reference](https://cli.vuejs.org/config/).
