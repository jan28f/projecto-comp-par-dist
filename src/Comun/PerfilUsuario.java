package Comun;

import java.io.Serializable;
import java.util.ArrayList;

public class PerfilUsuario implements Serializable {
    private String nombre;
    private ArrayList<String> seguidores;
    private ArrayList<String> seguidos;
    private ArrayList<Publicacion> publicaciones;

    public PerfilUsuario(String nombre) {
        this.nombre = nombre;
        this.seguidores = new ArrayList<String>();
        this.seguidos = new ArrayList<String>();
        this.publicaciones = new ArrayList<>();
    }

    public String getNombre() {
        return nombre;
    }
    public void agregarPublicacion(Publicacion pub) {
        publicaciones.add(pub);
    }
}
