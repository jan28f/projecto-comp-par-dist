package Comun.Sesion;

import java.io.Serializable;

public class SolicitudInicio implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String usuario;
    private final long horaConexion;

    public SolicitudInicio(String usuario, long horaConexion) {
        this.usuario = usuario;
        this.horaConexion = horaConexion;
    }

    public String getUsuario() {
        return usuario;
    }

    public long getHoraConexion() {
        return horaConexion;
    }
}
