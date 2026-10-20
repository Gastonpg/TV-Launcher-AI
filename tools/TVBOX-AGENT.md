# TV Box Agent (prototipo de solo lectura)

Este pequeño servicio corre dentro de Termux del TV Box y permite consultar información del dispositivo desde otro equipo de la misma red Wi-Fi. Es independiente de la APK de TV Launcher PG y no cambia el proceso de compilación del launcher.

## Seguridad y alcance

- API HTTP de solo lectura.
- Token aleatorio guardado en `~/.config/tvbox-agent/token` con permisos de archivo restrictivos.
- No ofrece una terminal remota, no acepta comandos arbitrarios y no tiene endpoints para instalar, borrar o modificar paquetes.
- Escucha en el puerto 8765. Usarlo únicamente en una red local de confianza; no configurar port forwarding ni exponerlo directamente a Internet.
- Esta primera versión no conecta por sí sola el servicio con ChatGPT en la nube. Para eso haría falta una integración/relay adicional, con autenticación y controles propios. Mientras tanto, el POCO puede consultar la API y compartir aquí el resultado.

## Instalar en Termux del TV Box

Desde Termux del TV Box:

```sh
pkg install python curl
curl -L -o "$HOME/tvbox_agent.py" "https://raw.githubusercontent.com/Gastonpg/TV-Launcher-AI/tvbox-agent-prototype/tools/tvbox_agent.py"
python "$HOME/tvbox_agent.py"
```

En el primer arranque aparecerá un token. Guardarlo en un lugar privado. El servicio permanece en primer plano mientras esa sesión de Termux siga activa; detenerlo con Ctrl+C. No configurar todavía el arranque automático hasta probarlo manualmente.

## Consultarlo desde Termux del POCO

Reemplazar `PEGA_EL_TOKEN` por el token mostrado en Termux del TV Box:

```sh
curl -sS -H "Authorization: Bearer PEGA_EL_TOKEN" "http://192.168.110.35:8765/v1/health"
curl -sS -H "Authorization: Bearer PEGA_EL_TOKEN" "http://192.168.110.35:8765/v1/diagnostics"
```

Si la dirección IP del TV Box cambia, actualizarla en la URL. No publicar ni pegar el token en chats.

## Endpoints de solo lectura

- `GET /v1/health` — estado del servicio.
- `GET /v1/device` — propiedades básicas, comprobación de root y estado de SELinux.
- `GET /v1/launchers` — candidatos HOME, resolución actual y presencia de los launchers.
- `GET /v1/packages` — paquetes de terceros.
- `GET /v1/packages?contains=smarttube` — filtrar por texto del nombre del paquete.
- `GET /v1/diagnostics` — informe combinado para diagnóstico.

Cualquier solicitud sin token válido devuelve 401. Los métodos POST, PUT y DELETE están bloqueados.
