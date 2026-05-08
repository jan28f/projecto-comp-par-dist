package Comun;

import java.io.Serializable;
import java.util.ArrayList;

public class PerfilUsuario implements Serializable {
    private String nombre;
    private ArrayList<String> seguidores;
    private ArrayList<String> seguidos;

    public PerfilUsuario(String nombre) {
        this.nombre = nombre;
        this.seguidores = new ArrayList<String>();
        this.seguidos = new ArrayList<String>();
    }

    public String getNombre() {
        return nombre;
    }
}
