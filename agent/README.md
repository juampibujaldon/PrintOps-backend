# Agente local de PrintOps

Lee el estado de las impresoras del taller y lo envía a PrintOps. Corre en una computadora
(o Raspberry Pi) **dentro de la red del taller**: el backend en la nube no puede llegar a
las impresoras de una red local, así que el agente las consulta y empuja los datos.

Soporta:

| Tipo | Fuente | Qué se necesita |
|---|---|---|
| `octoprint` | API REST de OctoPrint | URL y API key (Settings → API) |
| `moonraker` | Moonraker (Klipper: Mainsail/Fluidd) | URL (`http://ip:7125`) |
| `bambu` | MQTT local de Bambu Lab | IP, número de serie y código de acceso LAN |

## Qué hace

Cada `intervalSeconds` manda por impresora: estado (imprimiendo, pausada, terminada, error,
desconectada), progreso, temperaturas, nombre del trabajo y tiempo restante. Al terminar un
trabajo manda además las **horas y gramos reales** consumidos. Con eso PrintOps:

- muestra el estado en vivo en el plano del taller y en el detalle de la impresora;
- arranca y cierra solo los trabajos de la cola asignados a esa impresora;
- suma el uso real a la impresora (alimenta las reglas de mantenimiento y el desgaste);
- avisa al supervisor si la impresora informa un error.

## Uso

1. En PrintOps, abrí la impresora → **Integración** → elegí el tipo → **Generar token**.
   El token se muestra una sola vez.
2. Copiá `config.example.json` a `config.json` y completá cada impresora con su token.
3. Con Node 18 o superior:

```bash
cd agent
npm install
npm start            # usa ./config.json
node src/index.js /ruta/a/otra-config.json
```

Para dejarlo corriendo como servicio podés usar `pm2 start src/index.js --name printops-agent`
o una unidad de systemd.

## Notas

- Gramos: OctoPrint y Moonraker informan milímetros de filamento; se convierten con
  `filamentDiameter` (default 1,75 mm) y `filamentDensity` (default 1,24 g/cm³, PLA).
- Bambu no informa gramos ni tiempo total por MQTT: se mide el tiempo entre el inicio y el
  fin del trabajo; los gramos quedan los estimados del trabajo de la cola.
- Si se rota o borra la integración en PrintOps, el token viejo deja de funcionar (401).

```bash
npm test   # tests de la traducción de cada fuente
```
