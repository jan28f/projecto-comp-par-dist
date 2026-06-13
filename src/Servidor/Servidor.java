package Servidor;

import java.util.HashMap;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;

public class Servidor {
    private String id;
    private int puerto_clientes = -1;
    private int puerto_nodos = -1;
    private HashMap<String, InfoNodo> nodos;
    private int reloj;

    public void mostrarEstadoNodos() {
        System.out.println("---Estado nodos---");
        System.out.println("ID\t| IP\t| puerto_clientes\t| puerto_nodos\t");
        for (String id : nodos.keySet()) {
            InfoNodo info = nodos.get(id);
            String estado = info.getActivo() ? "ACTIVO" : "CAÍDO";
            System.out.println(id + "\t| " + info.getIp() + "\t| " +
                    info.getPuerto_cliente() + "\t\t| " +
                    info.getPuerto_servidor() + "\t\t| " + estado);
        }
    }

    public String getId() {
        return id;
    }

    public InfoNodo obtenerNodo(String idNodo) {
        return nodos.get(idNodo);
    }
    public HashMap<String, InfoNodo> obtenerNodos() {
        return nodos;
    }

    public synchronized int incrementarReloj() {
        reloj++;
        return reloj;
    }

    public synchronized int actualizarReloj(int relojRecibido) {
        this.reloj = Math.max(this.reloj, relojRecibido) + 1;
        return relojRecibido;
    }

    public synchronized int getReloj() {
        return reloj;
    }

    protected void escuchaNodos() {
        ServerSocket servidorNodos = null;

        try {
            servidorNodos = new ServerSocket(puerto_nodos);
            System.out.println("Escuchando nodos desde el puerto " + puerto_nodos);

            while (true) {
                Socket nodo = servidorNodos.accept();

                (new Thread(new ManejoNodo(nodo, this))).start();
            }
        }
        catch (BindException e) {
            System.out.println("Error: Puerto ya en uso");
        }
        catch (IllegalArgumentException e) {
            System.out.println("Error: Puerto fuera de rango valido");
        }
        catch (IOException e) {
            System.out.println("Error al iniciar el servidor");
        }
        finally {
            if (servidorNodos != null && !servidorNodos.isClosed()) {
                try {
                    servidorNodos.close();
                }
                catch (IOException e) {
                    System.out.println("Error al cerrar el servidor");
                }
            }
            System.out.println("Servidor cerrado correctamente");
        }
    }

    private void escuchaClientes() {
        ServerSocket servidorClientes = null;

        try {
            servidorClientes = new ServerSocket(puerto_clientes);
            System.out.println("Escuchando clientes desde el puerto " + puerto_clientes);

            while (true) {
                Socket cliente = servidorClientes.accept();

                (new Thread(new ManejoCliente(cliente))).start();
            }
        }
        catch (BindException e) {
            System.out.println("Error: Puerto ya en uso");
        }
        catch (IllegalArgumentException e) {
            System.out.println("Error: Puerto fuera de rango valido");
        }
        catch (IOException e) {
            System.out.println("Error al iniciar el servidor");
        }
        finally {
            if (servidorClientes != null && !servidorClientes.isClosed()) {
                try {
                    servidorClientes.close();
                }
                catch (IOException e) {
                    System.out.println("Error al cerrar el servidor");
                }
            }
            System.out.println("Servidor cerrado correctamente");
        }
    }

    private void cargarConfiguracion(String id) {
        System.out.println("Cargando configuración del servidor...");
        String ruta_archivo = "src/Servidor/nodos.csv";
        try {
            BufferedReader archivo = new BufferedReader(new FileReader(ruta_archivo));
            String linea;
            nodos = new HashMap<>();

            while ((linea = archivo.readLine()) != null) {
                String[] partes = linea.split(",");
                String ip = partes[1].trim();
                int puerto_clientes =  Integer.parseInt(partes[2].trim());
                int puerto_nodos =  Integer.parseInt(partes[3].trim());

                if (partes[0].equals(id)) {
                    this.id = partes[0].trim();
                    this.ip = ip;
                    this.puerto_clientes = puerto_clientes;
                    this.puerto_nodos = puerto_nodos;
                }
                else {
                    nodos.put(partes[0], new InfoNodo(ip, puerto_clientes, puerto_nodos));
                }
            }
            archivo.close();
        }
        catch (IOException e) {
            System.out.println("Error al leer el archivo de configuración");
        }
        catch (NumberFormatException e) {
            System.out.println("Error al convertir de texto a numero");
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Error: Se debe ingresar la id del nodo");
            System.exit(1);
        }
        String id = args[0];

        System.out.println("Iniciando servidor");
        Servidor servidor = new Servidor();
        servidor.cargarConfiguracion(id);
        servidor.mostrarEstadoNodos();
        (new Thread(servidor::escuchaNodos)).start();
        (new Thread(new EmisorLatidos(servidor))).start();
        (new Thread(new MonitorLatidos(servidor))).start();
        servidor.escuchaClientes();
    }
}