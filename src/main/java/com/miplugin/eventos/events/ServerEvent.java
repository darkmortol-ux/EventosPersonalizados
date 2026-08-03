package com.miplugin.eventos.events;

/**
 * Contrato que debe cumplir cada evento personalizado del servidor.
 */
public interface ServerEvent {

    /** Nombre corto usado en comandos y config (ej: "meteor-shower") */
    String getId();

    /** Nombre bonito para mostrar en el chat */
    String getDisplayName();

    /** Inicia el evento. Debe ser seguro llamarlo aunque ya esté activo (no hace nada en ese caso). */
    void start();

    /** Detiene el evento y limpia cualquier tarea/estado pendiente. */
    void stop();

    /** Indica si el evento está corriendo actualmente */
    boolean isRunning();

    /**
     * Indica si el evento puede activarse ahora mismo (ej: revisa min-players).
     * Se llama justo antes de start() cuando toca el ciclo automático.
     */
    boolean canStart();
}
