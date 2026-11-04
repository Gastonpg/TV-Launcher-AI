# PG Control Agent API

Protocol 1. Local HTTP port 8765. Android 9 / API 28.

Pairing: TV Launcher PG > Personalizar > Conectar PG Control del celular. Use the displayed address and bearer token on the same trusted Wi-Fi network.

Authenticated endpoints: GET /v1/device, GET /v1/packages, GET /v1/launcher, GET /v1/launcher/app-configs.

GET /v1/health is unauthenticated and returns service status only.

POST /v1/launcher/app-config accepts packageName, optional label, cardWidthDp and cardHeightDp. Only installed packages are accepted. Width is clamped to 80-320 dp and height to 48-220 dp. The launcher refreshes its app rail after changes.

Security boundary: no arbitrary shell, ADB, root, uninstall, or disable-package endpoint. Development transport is HTTP; use only on a trusted private Wi-Fi network until encrypted pairing is implemented.
