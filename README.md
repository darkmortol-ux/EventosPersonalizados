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

## Requisitos para compilar

- JDK 21
- Maven 3.9+
- Conexión a internet (para descargar `paper-api` desde PaperMC y `VaultAPI` desde JitPack)

> Este proyecto se generó en un entorno sin acceso a internet, por lo que **no se pudo
> compilar ni probar automáticamente**. Revisa el código y compílalo en tu máquina antes
> de usarlo en un servidor de producción.

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

## Cómo compilar

```bash
cd EventosPersonalizados
mvn clean package
```

El .jar final quedará en `target/EventosPersonalizados-1.0.0.jar`. Cópialo a la carpeta
`plugins/` de tu servidor Paper 1.21.11.

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

## Notas importantes / próximos pasos sugeridos

1. **Economía (Vault)**: ya está conectada. `random-boss` y `parkour-race` pagan
   dinero real vía Vault si tienes un plugin de economía instalado; si no, avisan
   en el chat sin romper el resto del evento.
2. **Cofre Supremo**: aparece dentro de un radio de `max-distance` bloques (por
   defecto 1000) alrededor de la coordenada 0,0 del mundo principal del servidor
   (el primero en `getWorlds()`, normalmente el overworld). Tiene `time-limit-minutes`
   (10 por defecto) para ser encontrado y abierto; si nadie lo abre a tiempo, o en
   cuanto alguien lo abre, el bloque desaparece automáticamente. Mientras está activo,
   se anuncian sus coordenadas por chat cada `reminder-interval-minutes` (2 por defecto);
   durante los últimos `final-warning-minutes` (2 por defecto) se avisa que está a punto
   de desaparecer, repitiendo ese aviso cada `final-warning-interval-seconds` (30 por
   defecto). Con la configuración por defecto (10 minutos), esto significa recordatorios
   en el minuto 2, 4 y 6, y avisos de "está por desaparecer" cada 30 segundos entre el
   minuto 8 y el 10.
3. **Carrera de Parkour**: la pista se genera con lana de colores sobre bloques de aire;
   puedes ajustar el algoritmo de generación en `generateTrack()` si quieres un trazado
   más elaborado (por ejemplo con posibilidad de vacío bajo las plataformas y checkpoints
   con partículas).
4. **Persistencia entre reinicios**: los temporizadores de `interval-minutes` se reinician
   cada vez que el servidor arranca (no recuerdan cuánto faltaba para el próximo evento
   antes de un reinicio). Si te interesa, se puede guardar el timestamp en un archivo.
5. Puedes cambiar los mensajes de chat (colores, textos) directamente en cada clase de
   evento dentro de `src/main/java/com/miplugin/eventos/events/`.
