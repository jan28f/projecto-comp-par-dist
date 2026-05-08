package Comun;

import java.io.Serializable;

public class PeticionConexion implements Serializable {
    private String usuario;
    private long horaConexion;

    public PeticionConexion (String usuario, long horaConexion) {
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
