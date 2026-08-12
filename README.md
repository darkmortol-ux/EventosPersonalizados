# EventosPersonalizados

Plugin para Paper 1.21.11 con 7 eventos personalizados de servidor, todos programables
y configurables desde `config.yml`.

## Eventos incluidos

| Evento | ID (config/comando) | Se activa |
|---|---|---|
| Lluvia de Meteoritos | `meteor-shower` | cada X minutos |
| Invasión de Mobs | `mob-invasion` | cada X minutos, cantidad configurable |
| Noche Eterna | `eternal-night` | cada X minutos, XP y loot configurable |
| Tormenta Tóxica | `toxic-storm` | cada X minutos |
| Cofre Supremo | `supreme-chest` | cada X minutos, aparece cerca de la coordenada 0,0 |
| Jefe Aleatorio | `random-boss` | cada X minutos + mínimo de jugadores |
| Carrera de Parkour | `parkour-race` | cada X minutos + mínimo de jugadores |


## Integración con Vault (recompensas en dinero)

El plugin se conecta automáticamente a **Vault** para pagar las recompensas de
`random-boss` (`reward-money`) y `parkour-race` (`reward-first/second/third`).

Para que funcione en tu servidor necesitas:
1. Instalar [Vault](https://www.spigotmc.org/resources/vault.34315/) en `plugins/`
2. Instalar un plugin de economía compatible con Vault (por ejemplo **EssentialsX**,
   con el módulo `EssentialsX Economy`)

Si Vault o un plugin de economía no están instalados, el plugin **no se rompe**: simplemente
avisa en la consola y en el chat que la recompensa en dinero no está disponible, pero el
resto del evento (ítems, XP, mensajes) sigue funcionando con normalidad.

`softdepend: [Vault]` ya está declarado en `plugin.yml`, y la conexión se hace en el
evento `ServerLoadEvent` (cuando todos los plugins ya terminaron de cargar), así que no
importa el orden en que se instalen los plugins.


## Comandos

- `/eventos list` — muestra todos los eventos y su estado
- `/eventos start <evento>` — fuerza el inicio de un evento
- `/eventos stop <evento>` — fuerza la detención de un evento
- `/eventos reload` — recarga `config.yml` sin reiniciar el servidor

Permiso requerido: `eventos.admin` (por defecto, solo operadores/OP)

## Configuración

Todo se ajusta en `plugins/EventosPersonalizados/config.yml`:
- `interval-minutes`: cada cuánto se activa el evento automáticamente
- `duration-seconds`: cuánto dura el evento una vez activo
- Parámetros específicos de cada evento (cantidad de mobs, XP, loot, jugadores mínimos, etc.)

---
Mis Plugins

https://github.com/darkmortol-ux/RangosMC
https://github.com/darkmortol-ux/BordePersonalizado
https://github.com/darkmortol-ux/ProteccionAreas
https://github.com/darkmortol-ux/SistemaClases
