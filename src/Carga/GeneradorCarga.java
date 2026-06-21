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

    private static void imprimirMetricasLatencia(List<Long> latencias, String etiqueta) {
        if (latencias.isEmpty()) {
            System.out.println(etiqueta + " - Sin datos de latencia");
            return;
        }
        double media = latencias.stream().mapToLong(Long::longValue).average().orElse(0);
        Collections.sort(latencias);
        int size = latencias.size();
        long p50 = latencias.get(size * 50 / 100);
        long p90 = latencias.get(size * 90 / 100);
        long p95 = latencias.get(size * 95 / 100);
        long p99 = latencias.get(size * 99 / 100);

        System.out.println(etiqueta + " - Latencia media (ms): " + String.format("%.2f", media));
        System.out.println(etiqueta + " - Percentil 50: " + p50 + " ms");
        System.out.println(etiqueta + " - Percentil 90: " + p90 + " ms");
        System.out.println(etiqueta + " - Percentil 95: " + p95 + " ms");
        System.out.println(etiqueta + " - Percentil 99: " + p99 + " ms");
    }

    private static void contarMensajesCoordinacion(String[][] nodosConfig) {
        long totalEleccion = 0;
        long totalOk = 0;
        long totalCoordinador = 0;
        long totalRaRequest = 0;
        long totalRaReply = 0;

        for (String[] cfg : nodosConfig) {
            Path logPath = Paths.get("log_" + cfg[0] + ".txt");
            if (!Files.exists(logPath)) continue;
            try {
                for (String linea : Files.readAllLines(logPath)) {
                    if (linea.contains("ELECCION")) totalEleccion++;
                    if (linea.contains("OK")) totalOk++;
                    if (linea.contains("SOY COORDINADOR") || linea.contains("NUEVO_COORDINADOR")) totalCoordinador++;
                    if (linea.contains("RA_REQUEST")) totalRaRequest++;
                    if (linea.contains("RA_REPLY")) totalRaReply++;
                }
            } catch (IOException e) {}
        }

        System.out.println("\n=== MENSAJES DE COORDINACION (TOTALES) ===");
        System.out.println("ELECCION: " + totalEleccion);
        System.out.println("OK: " + totalOk);
        System.out.println("COORDINADOR: " + totalCoordinador);
        System.out.println("RA_REQUEST: " + totalRaRequest);
        System.out.println("RA_REPLY: " + totalRaReply);
    }

    public static void main(String[] args) throws Exception {
        int numClientes = 50;
        long duracionSegundos = 60;
        long tiempoAntesFalla = 30;

        String[][] nodosConfig = {
                {"1", "1003", "1004"},
                {"2", "1005", "1006"},
                {"3", "1007", "1008"},
                {"4", "1009", "1010"}
        };

        Path rutaCSV = Paths.get("src", "Servidor", "nodos.csv");
        if (!Files.exists(rutaCSV)) {
            Files.createDirectories(rutaCSV.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(rutaCSV)) {
                for (String[] cfg : nodosConfig) {
                    writer.write(cfg[0] + ",127.0.0.1," + cfg[1] + "," + cfg[2]);
                    writer.newLine();
                }
            }
        }

        List<Process> procesosNodos = new ArrayList<>();
        for (String[] cfg : nodosConfig) {
            String id = cfg[0];
            ProcessBuilder pb = new ProcessBuilder(
                    "java", "-cp", System.getProperty("java.class.path"),
                    "Servidor.Servidor", id
            );
            pb.inheritIO();
            Process p = pb.start();
            procesosNodos.add(p);
        }

        Thread.sleep(5000);

        List<NodoInfo> nodosInfo = new ArrayList<>();
        for (String[] cfg : nodosConfig) {
            nodosInfo.add(new NodoInfo("127.0.0.1", Integer.parseInt(cfg[1])));
        }

        AtomicLong exitoOpsNormales = new AtomicLong(0);
        AtomicLong errorOpsNormales = new AtomicLong(0);
        List<Long> latenciasNormales = Collections.synchronizedList(new ArrayList<>());
        AtomicLong exitoOpsCaida = new AtomicLong(0);
        AtomicLong errorOpsCaida = new AtomicLong(0);
        List<Long> latenciasCaida = Collections.synchronizedList(new ArrayList<>());

        long inicioGlobal = System.currentTimeMillis();
        ExecutorService pool = Executors.newFixedThreadPool(numClientes);
        List<CargaCliente> clientes = new ArrayList<>();

        for (int i = 0; i < numClientes; i++) {
            String user = "carga" + i;
            int parejaIndex = numClientes - 1 - i;
            String destino = "carga" + parejaIndex;

            CargaCliente c = new CargaCliente(user, duracionSegundos, destino,
                    exitoOpsNormales, errorOpsNormales, latenciasNormales,
                    exitoOpsCaida, errorOpsCaida, latenciasCaida,
                    inicioGlobal, nodosInfo);
            clientes.add(c);
            pool.submit(c);
        }

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
        }

        System.out.println("=== INDUCIENDO FALLA: nodo " + nodosConfig[indiceCoordinador][0] + " ===");
        Process nodoCoordinador = procesosNodos.get(indiceCoordinador);
        nodoCoordinador.destroy();
        Thread.sleep(2000);

        pool.shutdown();
        boolean terminado = pool.awaitTermination(duracionSegundos + 30, TimeUnit.SECONDS);
        if (!terminado) pool.shutdownNow();

        long totalExitosN = exitoOpsNormales.get();
        long totalErroresN = errorOpsNormales.get();
        long totalExitosC = exitoOpsCaida.get();
        long totalErroresC = errorOpsCaida.get();

        long totalExitos = totalExitosN + totalExitosC;
        long totalErrores = totalErroresN + totalErroresC;
        long totalOps = totalExitos + totalErrores;
        double throughput = (double) totalOps / duracionSegundos;

        System.out.println("\n=== RESULTADOS DE CARGA ===");
        System.out.println("Throughput global: " + String.format("%.2f", throughput) + " ops/seg");
        System.out.println("Operaciones totales exitosas: " + totalExitos);
        System.out.println("Operaciones totales con error: " + totalErrores);

        System.out.println("\n--- PERIODO NORMAL ---");
        System.out.println("Exitos: " + totalExitosN);
        System.out.println("Errores: " + totalErroresN);
        imprimirMetricasLatencia(latenciasNormales, "Normal");

        System.out.println("\n--- PERIODO TRAS CAIDA ---");
        System.out.println("Exitos: " + totalExitosC);
        System.out.println("Errores: " + totalErroresC);
        imprimirMetricasLatencia(latenciasCaida, "Caida");

        contarMensajesCoordinacion(nodosConfig);

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
            System.out.println("Tiempo de recuperacion maximo detectado por clientes: " + tiempoRecuperacion + " ms");
        }

        for (Process p : procesosNodos) {
            if (p.isAlive()) p.destroy();
        }
    }
}