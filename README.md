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
| Mercado Negro | `mercado-negro` | cada X minutos, 50 ofertas con stock compartido |

## Requisitos para compilar

- JDK 21
- Maven 3.9+
- Conexión a internet (para descargar `paper-api` desde PaperMC y `VaultAPI` desde JitPack)

> Este proyecto se generó en un entorno sin acceso a internet, por lo que **no se pudo
> compilar ni probar automáticamente**. Revisa el código y compílalo en tu máquina antes
> de usarlo en un servidor de producción.

## Integración con Vault (recompensas en dinero)

El plugin se conecta automáticamente a **Vault** para pagar las recompensas de
`random-boss` (`reward-money`).

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
- `/eventos menu` — abre un menú gráfico (inventario) para iniciar o pausar eventos con click

### Un solo evento a la vez

Solo puede haber **1 evento activo al mismo tiempo** en todo el servidor. Esto aplica tanto a
la activación automática (por `interval-minutes`) como a la manual (`/eventos start`, `/eventos
menu`): si dos eventos coinciden en el mismo ciclo de tiempo, o si intentas iniciar uno mientras
otro ya está corriendo, el segundo simplemente no se activa (se reintenta en su próximo ciclo
automático). El menú gráfico también respeta esta regla: mientras un evento está activo, los
demás se muestran en rojo indicando que hay que esperar o pausar el actual primero.

### Menú gráfico (`/eventos menu`)

Abre un inventario con los 7 eventos como íconos. Cada uno muestra su estado actual:
- **Verde (ACTIVO)**: click para **pausarlo** (lo detiene igual que `/eventos stop`)
- **Amarillo (Inactivo, disponible)**: click para **iniciarlo**
- **Rojo (Inactivo, bloqueado)**: hay otro evento activo; no se puede iniciar hasta que termine o
  lo pauses

El menú se actualiza solo después de cada click. Requiere el permiso `eventos.admin` (mismo que
el resto del comando `/eventos`).

> ⚠️ **Nota sobre "pausar":** técnicamente "pausar" un evento lo **detiene por completo** (igual
> que `/eventos stop`), limpiando cualquier progreso (mobs invocados, cofre supremo colocado,
> etc.). No existe una pausa real que reanude el evento exactamente donde
> quedó — los eventos no fueron diseñados para eso. Si necesitas una pausa/reanudación real
> (por ejemplo, congelar el temporizador del jefe a mitad de pelea y continuar después), avísame
> y lo agrego, pero implica cambios más profundos en cada clase de evento.

Permiso requerido: `eventos.admin` (por defecto, solo operadores/OP)

### Mercado Negro (`mercado-negro`)

Al activarse, aparece un aldeano especial ("Comerciante Misterioso") que, al hacerle click
derecho, abre una tienda con **50 ofertas distintas** (armaduras, armas, herramientas
encantadas nivel VI o más, materiales raros, totems, elytra, etc. — todo editable en
`config.yml` → `mercado-negro.ofertas`).

**Asignar dónde aparece el aldeano** (opción B — en vivo, sin tocar config ni reiniciar):
```
/eventos setloc mercado-negro
```
Párate donde quieras que aparezca, ejecuta el comando, y queda guardado en `ubicaciones.yml`
(persiste entre reinicios). Si nunca lo asignas, aparece en el spawn del primer mundo del
servidor como respaldo.

**Stock compartido entre todos los jugadores**: cada oferta tiene un `stock` fijo que se
descuenta para TODO el servidor, no por jugador — si alguien compra el único Elytra
disponible, desaparece para los demás al instante. El menú usa reserva-antes-de-cobrar para
que dos compras simultáneas nunca dejen el stock en negativo.

El mercado se cierra (el aldeano desaparece) al acabarse el `duration-seconds`, o antes si
tú lo pausas desde `/eventos menu` o `/eventos stop mercado-negro`.

## Configuración

Todo se ajusta en `plugins/EventosPersonalizados/config.yml`:
- `interval-minutes`: cada cuánto se activa el evento automáticamente
- `duration-seconds`: cuánto dura el evento una vez activo
- Parámetros específicos de cada evento (cantidad de mobs, XP, loot, jugadores mínimos, etc.)

## Notas importantes / próximos pasos sugeridos

1. **Economía (Vault)**: ya está conectada. `random-boss` paga dinero real vía Vault
   si tienes un plugin de economía instalado; si no, avisa en el chat sin romper el
   resto del evento.
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

   **Loot con probabilidad (`chance`)**: cada item de `supreme-chest.loot` en `config.yml`
   ahora tiene un campo `chance` en escala de 0.05 a 10.0, donde `10.0` = 100% de probabilidad
   de aparecer, `5.0` = 50%, `1.0` = 10%, y `0.05` = 0.5% (el mínimo). Cada item se sortea de
   forma **independiente** de los demás — pueden salir varios a la vez, uno solo, o incluso
   ninguno si a todos les toca fallar. Si un item no trae `chance` en el config, se asume
   `10.0` (100%, siempre aparece), para no romper configuraciones viejas. Ya dejé 19 items de
   ejemplo con variedad de rareza (desde flechas comunes hasta un Elytra al 0.05%) — ajusta
   las cantidades (`min`/`max`) y probabilidades (`chance`) a tu gusto.
3. **Persistencia entre reinicios**: los temporizadores de `interval-minutes` se reinician
   cada vez que el servidor arranca (no recuerdan cuánto faltaba para el próximo evento
   antes de un reinicio). Si te interesa, se puede guardar el timestamp en un archivo.
4. Puedes cambiar los mensajes de chat (colores, textos) directamente en cada clase de
   evento dentro de `src/main/java/com/miplugin/eventos/events/`.
