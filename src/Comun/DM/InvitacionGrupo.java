package Comun.DM;

import java.io.Serializable;

public class InvitacionGrupo implements Serializable {
    private static final long serialVersionUID = 1L;
    private String nombreGrupo;
    private String idGrupo;
    private String invitadoPor;

    public InvitacionGrupo(String nombreGrupo, String idGrupo, String invitadoPor) {
        this.nombreGrupo = nombreGrupo;
        this.idGrupo = idGrupo;
        this.invitadoPor = invitadoPor;
    }

    public String getNombreGrupo() {
        return nombreGrupo;
    }
    public void setNombreGrupo(String nombreGrupo) {
        this.nombreGrupo = nombreGrupo;
    }
    public String getIdGrupo() {
        return idGrupo;
    }
    public String  getInvitadoPor() {
        return invitadoPor;
    }
}
