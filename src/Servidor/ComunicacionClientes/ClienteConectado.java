package Servidor.ComunicacionClientes;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayDeque;

public class ClienteConectado {
    private final String usuario;
    private final ObjectOutputStream salida;
    private volatile boolean activo = true;
    private final ArrayDeque<Object> cola;
    private final int capacidad_cola;
    private final Thread escritor;

    public ClienteConectado(String usuario, ObjectOutputStream salida, int capacidad_cola) {
        this.usuario = usuario;
        this.salida = salida;
        this.cola = new ArrayDeque<>();
        this.capacidad_cola = capacidad_cola;
        this.escritor = new Thread(this::enviarCola);
        this.escritor.start();
    }

    private synchronized Object sacar() throws InterruptedException {
        while (cola.isEmpty()) {
            wait();
        }
        return cola.removeFirst();
    }

    private synchronized void insertar(Object mensaje) {
        if (cola.size() == capacidad_cola) {
            cola.removeFirst();
        }
        cola.addLast(mensaje);
        notifyAll();
    }

    public void enviar(Object mensaje) {
        if (!activo) {
            return;
        }
        insertar(mensaje);
    }

    public void enviarCola() {
        boolean primero = true;
        try {
            while (activo) {
                Object mensaje = sacar();
                if (!primero) {
                    salida.reset();
                }
                primero = false;
                salida.writeObject(mensaje);
                salida.flush();
            }
        }
        catch (InterruptedException ignored) {}
        catch (IOException e) {
            System.out.println("Error de comunicación en el canal de " + usuario);
        }
        finally {
            activo = false;
        }
    }

    public void cerrar() {
        activo = false;
        escritor.interrupt();
        try {
            salida.close();
        }
        catch (IOException ignored) {}
    }
}
