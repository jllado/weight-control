alter table users
    drop column oura_access_token,
    drop column oura_refresh_token,
    drop column oura_token_expires_at,
    drop column oura_scopes,
    drop column oura_last_sync_at,
    drop column oura_connected_at,
    drop column oura_sync_error;

alter table sleeps
    drop column oura_document_id,
    drop column sleep_score,
    drop column efficiency,
    drop column latency;
