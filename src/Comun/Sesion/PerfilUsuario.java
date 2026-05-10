package Comun.Sesion;

import Comun.DM.InvitacionGrupo;
import Comun.Publiaciones.Publicacion;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class PerfilUsuario implements Serializable {
    private String nombre;
    private ArrayList<String> seguidores;
    private ArrayList<String> seguidos;
    private ArrayList<Publicacion> publicaciones;
    private HashMap<String, String> misGrupos;
    private HashMap<String, InvitacionGrupo> invitacionesPendientes;

    public PerfilUsuario(String nombre) {
        this.nombre = nombre;
        this.seguidores = new ArrayList<String>();
        this.seguidos = new ArrayList<String>();
        this.publicaciones = new ArrayList<>();
        this.misGrupos = new HashMap<>();
        this.invitacionesPendientes = new HashMap<>();
    }

    public String getNombre() {
        return nombre;
    }
    public void agregarPublicacion(Publicacion pub) {
        publicaciones.add(pub);
    }
    public void agregarInvitacionGrupo(String nombreGrupo, String idGrupo, String creador) {
        invitacionesPendientes.put(nombreGrupo, new InvitacionGrupo(nombreGrupo, idGrupo, creador));
    }
    public InvitacionGrupo obtenerInvitacionGrupo(String nombreGrupo) {
        return invitacionesPendientes.get(nombreGrupo);
    }
    public void eliminarInvitacionGrupo(String nombreGrupo) {
        invitacionesPendientes.remove(nombreGrupo);
    }
    public void agregarGrupo(String nombreGrupo, String identificadorGrupo) {
        misGrupos.put(nombreGrupo, identificadorGrupo);
    }
    public String obtenerIDGrupo(String nombreGrupo) {
        return misGrupos.get(nombreGrupo);
    }
    public void eliminarGrupo(String nombreGrupo) {
        misGrupos.remove(nombreGrupo);
    }
}
