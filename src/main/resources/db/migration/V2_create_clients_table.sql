CREATE TABLE oauth_clients (
    client_id VARCHAR(255) PRIMARY KEY,
    client_secret VARCHAR(255) NOT NULL,
    name VARCHAR(255)
);

CREATE TABLE oauth_clients_redirect_uris (
    client_id VARCHAR(255) REFERENCES oauth_clients(client_id),
    redirect_uris VARCHAR(255)
);

CREATE TABLE oauth_clients_allowed_scopes (
    client_id VARCHAR(255) REFERENCES oauth_clients(client_id),
    allowed_scopes VARCHAR(255)
);