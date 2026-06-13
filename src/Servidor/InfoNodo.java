package Servidor;

public class InfoNodo {
    private final String ip;
    private final int puerto_cliente;
    private final int puerto_servidor;
    private boolean activo;
    private long ultimoLatido;

    public InfoNodo(String ip, int puerto_cliente, int puerto_servidor) {
        this.ip = ip;
        this.puerto_cliente = puerto_cliente;
        this.puerto_servidor = puerto_servidor;
        this.activo = true;
        this.ultimoLatido = System.currentTimeMillis();
    }

    public String getIp() {
        return ip;
    }
    public int getPuerto_cliente() {
        return puerto_cliente;
    }
    public int getPuerto_servidor() {
        return puerto_servidor;
    }
    public void setActivo(boolean activo) {
        this.activo = activo;
    }
    public boolean getActivo() {
        return activo;
    }
    public void setUltimoLatido(long ultimoLatido) {
        this.ultimoLatido = ultimoLatido;
    }
    public long getUltimoLatido() {
        return ultimoLatido;
    }
}
