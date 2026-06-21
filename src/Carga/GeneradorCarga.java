package Carga;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class GeneradorCarga {
    private static String obtenerCoordinadorReal(String[][] nodosConfig) throws IOException {
        String coordinadorFinal = null;
        long lamportMax = -1;

        for (String[] cfg : nodosConfig) {
            Path logPath = Paths.get("log_" + cfg[0] + ".txt");
            if (!Files.exists(logPath)) {
                continue;
            }
            for (String linea : Files.readAllLines(logPath)) {
                long lamportLinea = extraerLamport(linea);
                if (linea.contains("SOY COORDINADOR") && lamportLinea > lamportMax) {
                    lamportMax = lamportLinea;
                    coordinadorFinal = cfg[0];
                } else if (linea.contains("COORDINADOR establecido:") && lamportLinea > lamportMax) {
                    lamportMax = lamportLinea;
                    coordinadorFinal = linea.substring(linea.lastIndexOf(":") + 1).trim();
                }
            }
        }
        return coordinadorFinal;
    }

    private static long extraerLamport(String linea) {
        try {
            int inicio = linea.indexOf("lamport=") + 8;
            int fin = linea.indexOf("]", inicio);
            return Long.parseLong(linea.substring(inicio, fin));
        } catch (Exception e) {
            return -1;
        }
    }

    public static void main(String[] args) throws Exception {
        int numClientes = 50;
        long duracionSegundos = 60;
        long tiempoAntesFalla = 30;

        // Configuración de nodos (ID, puerto cliente, puerto nodo)
        String[][] nodosConfig = {
                {"1", "1003", "1004"},
                {"2", "1005", "1006"},
                {"3", "1007", "1008"},
                {"4", "1009", "1010"}
        };

        // Asegurar que el archivo nodos.csv existe en la ruta esperada
        Path rutaCSV = Paths.get("src", "Servidor", "nodos.csv");
        if (!Files.exists(rutaCSV)) {
            System.out.println("Creando archivo nodos.csv en " + rutaCSV.toAbsolutePath());
            Files.createDirectories(rutaCSV.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(rutaCSV)) {
                for (String[] cfg : nodosConfig) {
                    writer.write(cfg[0] + ",localhost," + cfg[1] + "," + cfg[2]);
                    writer.newLine();
                }
            }
        } else {
            System.out.println("Archivo nodos.csv ya existe en " + rutaCSV.toAbsolutePath());
        }

        // Lanzar nodos como procesos hijos
        List<Process> procesosNodos = new ArrayList<>();
        for (String[] cfg : nodosConfig) {
            String id = cfg[0];
            ProcessBuilder pb = new ProcessBuilder(
                    "java", "-cp", System.getProperty("java.class.path"),
                    "Servidor.Servidor", id
            );
            pb.inheritIO(); // redirige salida a la consola del generador
            Process p = pb.start();
            procesosNodos.add(p);
            System.out.println("Lanzado nodo " + id + " (PID: " + p.pid() + ")");
        }

        System.out.println("Esperando 5 segundos para que los nodos arranquen...");
        Thread.sleep(5000);

        // Crear lista de NodoInfo para los clientes
        List<NodoInfo> nodosInfo = new ArrayList<>();
        for (String[] cfg : nodosConfig) {
            nodosInfo.add(new NodoInfo("127.0.0.1", Integer.parseInt(cfg[1])));
        }

        // Generar usuarios y parejas (50 usuarios, emparejados: 0↔49, 1↔48, ...)
        List<String> usuarios = new ArrayList<>();
        for (int i = 0; i < numClientes; i++) {
            usuarios.add("carga" + i);
        }

        AtomicLong exitoOps = new AtomicLong(0);
        AtomicLong errorOps = new AtomicLong(0);
        List<Long> latencias = Collections.synchronizedList(new ArrayList<>());
        long inicioGlobal = System.currentTimeMillis();

        ExecutorService pool = Executors.newFixedThreadPool(numClientes);
        List<CargaCliente> clientes = new ArrayList<>();

        for (int i = 0; i < numClientes; i++) {
            String user = "carga" + i;
            int parejaIndex = numClientes - 1 - i;
            String destino = "carga" + parejaIndex;

            CargaCliente c = new CargaCliente(user, duracionSegundos, destino,
                    exitoOps, errorOps, latencias, inicioGlobal, nodosInfo);
            clientes.add(c);
            pool.submit(c);
        }

        System.out.println("Esperando " + tiempoAntesFalla + " segundos para inducir falla...");
        Thread.sleep(tiempoAntesFalla * 1000);

        String idCoordinadorReal = obtenerCoordinadorReal(nodosConfig);
        int indiceCoordinador = 0;
        if (idCoordinadorReal != null) {
            for (int i = 0; i < nodosConfig.length; i++) {
                if (nodosConfig[i][0].equals(idCoordinadorReal)) {
                    indiceCoordinador = i;
                    break;
                }
            }
        } else {
            System.out.println("No se pudo determinar el coordinador real desde los logs, se usara el nodo " + nodosConfig[0][0]);
        }

        System.out.println("=== INDUCIENDO FALLA: matando nodo " + nodosConfig[indiceCoordinador][0] + " (coordinador real) ===");
        Process nodoCoordinador = procesosNodos.get(indiceCoordinador);
        nodoCoordinador.destroy();
        Thread.sleep(2000); // dar tiempo a que el sistema detecte la caída

        pool.shutdown();
        boolean terminado = pool.awaitTermination(duracionSegundos + 30, TimeUnit.SECONDS);
        if (!terminado) pool.shutdownNow();

        // Estadísticas
        long totalExitos = exitoOps.get();
        long totalErrores = errorOps.get();
        long totalOps = totalExitos + totalErrores;

        System.out.println("\n=== RESULTADOS DE CARGA (SOLO MENSAJES POR PAREJAS) ===");
        System.out.println("Clientes: " + numClientes);
        System.out.println("Duración: " + duracionSegundos + " segundos");
        System.out.println("Operaciones exitosas: " + totalExitos);
        System.out.println("Operaciones con error: " + totalErrores);
        System.out.println("Total operaciones: " + totalOps);
        if (totalExitos > 0) {
            double media = latencias.stream().mapToLong(Long::longValue).average().orElse(0);
            System.out.println("Latencia media (ms): " + String.format("%.2f", media));
            Collections.sort(latencias);
            int size = latencias.size();
            System.out.println("Percentil 50: " + latencias.get(size * 50 / 100) + " ms");
            System.out.println("Percentil 90: " + latencias.get(size * 90 / 100) + " ms");
            System.out.println("Percentil 99: " + latencias.get(size * 99 / 100) + " ms");
        }

        // Análisis de falla
        long caidaMin = Long.MAX_VALUE;
        long recMax = 0;
        boolean algunaCaida = false;
        for (CargaCliente c : clientes) {
            if (c.haCaido()) {
                algunaCaida = true;
                if (c.getCaidaTimestamp() < caidaMin) caidaMin = c.getCaidaTimestamp();
                if (c.getRecuperacionTimestamp() > recMax) recMax = c.getRecuperacionTimestamp();
            }
        }
        if (algunaCaida) {
            long tiempoRecuperacion = recMax - caidaMin;
            System.out.println("\n=== FALLA INDUCIDA ===");
            System.out.println("Tiempo de recuperación (desde primera caída hasta última reconexión): " + tiempoRecuperacion + " ms");
        } else {
            System.out.println("\nNo se detectaron caídas.");
        }

        // Matar todos los procesos nodos al finalizar
        for (Process p : procesosNodos) {
            if (p.isAlive()) p.destroy();
        }
        System.out.println("Prueba finalizada.");
    }
}