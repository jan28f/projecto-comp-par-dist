package Comun;

import java.io.Serializable;

public class RespuestaInicio implements Serializable {
    private final boolean estado;
    private final String mensaje;

    public RespuestaInicio(boolean estado, String mensaje) {
        this.estado = estado;
        this.mensaje = mensaje;
    }

    public boolean getEstado() {
        return estado;
    }

    public String getMensaje() {
        return mensaje;
    }
}
