package Comun.DM;

import java.io.Serializable;

public class AccionGrupo implements Serializable {
    private static final long serialVersionUID = 1L;
    private String accion;
    private String nombreGrupo;

    public AccionGrupo(String accion, String nombreGrupo) {
        this.accion = accion;
        this.nombreGrupo = nombreGrupo;
    }

    public String getAccion() {
        return accion;
    }
    public String getNombreGrupo() {
        return nombreGrupo;
    }
}