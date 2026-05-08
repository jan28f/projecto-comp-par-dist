package Cliente;

import java.io.IOException;
import java.io.ObjectInputStream;
import Comun.Mensaje;

public class EscuchaCliente implements Runnable {
    private final ObjectInputStream entrada;

    public EscuchaCliente(ObjectInputStream entrada) {
        this.entrada = entrada;
    }

    public void run() {
        try {
            while (true) {
                Object obj = entrada.readObject();

                if (obj instanceof Mensaje) {
                    Mensaje msj = (Mensaje) obj;
                    System.out.println("\n[DM de " + msj.getRemitente() + "]: " + msj.getContenido());
                    System.out.print("> ");
                }
            }
        } catch (IOException e) {
            System.out.println("Error: " + e);
        } catch (ClassNotFoundException e) {
            System.out.println("Error: Clase del objeto desconocido");
        }
    }
}
