package Carga;

public class NodoInfo {
    private final String ip;
    private final int puertoCliente;

    public NodoInfo(String ip, int puertoCliente) {
        this.ip = ip;
        this.puertoCliente = puertoCliente;
    }

    public String getIp() { return ip; }
    public int getPuertoCliente() { return puertoCliente; }
}