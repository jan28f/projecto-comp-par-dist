package Servidor.ComunicacionNodos;

import java.util.HashMap;

public class MembresiaNodos {
    private final HashMap<String, InfoNodo> nodos = new HashMap<>();
    private final HashMap<String, ConexionNodo> conexiones = new HashMap<>();

    public void agregarNodo(String id, InfoNodo nodo) {
        nodos.put(id, nodo);
    }

    public java.util.Set<String> idsNodos() {
        return nodos.keySet();
    }

    public InfoNodo getNodo(String id) {
        return nodos.get(id);
    }

    public void mostrarEstado() {
        System.out.println("---Estado nodos---");
        System.out.println("ID\t| IP\t| puerto_clientes\t| puerto_nodos\t| estado");
        for (String id : nodos.keySet()) {
            InfoNodo info = nodos.get(id);
            String estado = info.getActivo() ? "ACTIVO" : "CAÍDO";
            System.out.println(id + "\t| " + info.getIp() + "\t| " +
                    info.getPuerto_cliente() + "\t\t| " +
                    info.getPuerto_servidor() + "\t\t| " + estado);
        }
    }

    public void registrarLatido(String id) {
        InfoNodo info = nodos.get(id);
        if (info == null) {
            return;
        }
        boolean estabaCaido = !info.getActivo();
        info.setActivo(true);
        info.setUltimoLatido(System.currentTimeMillis());
        if (estabaCaido) {
            System.out.println("El nodo " + id + " está activo");
            mostrarEstado();
        }
    }

    public boolean marcarCaido(String id) {
        InfoNodo info = nodos.get(id);
        if (info != null && info.getActivo()) {
            info.setActivo(false);
            System.out.println("El nodo " + id + " ha caído");
            return true;
        }
        return false;
    }

    public synchronized ConexionNodo getConexion(String id) {
        ConexionNodo conexion = conexiones.get(id);
        if (conexion == null) {
            InfoNodo info = nodos.get(id);
            if (info == null) {
                return null;
            }
            conexion = new ConexionNodo(id, info.getIp(), info.getPuerto_servidor());
            conexiones.put(id, conexion);
        }
        return conexion;
    }
}
