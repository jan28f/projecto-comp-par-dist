package Comun.DM;

import java.io.Serializable;
import java.util.ArrayList;

public class SolicitudGrupo implements Serializable {
    private String nombreGrupo;
    private ArrayList<String> integrantes;

    public SolicitudGrupo(String nombreGrupo, ArrayList<String> integrantes) {
        this.nombreGrupo = nombreGrupo;
        this.integrantes = integrantes;
    }

    public String getNombreGrupo() {
        return nombreGrupo;
    }
    public ArrayList<String> getIntegrantes() {
        return integrantes;
    }
}