package Carga;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalTime;
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

    private static long percentil(List<Long> latencias, int p) {
        if (latencias.isEmpty()) return -1;
        List<Long> copia = new ArrayList<>(latencias);
        Collections.sort(copia);
        return copia.get(copia.size() * p / 100);
    }

    private static long[] contarMensajesCoordinacion(String[][] nodosConfig) {
        long eleccion = 0;
        long ok = 0;
        long coordinador = 0;
        long raRequest = 0;
        long raReply = 0;

        for (String[] cfg : nodosConfig) {
            Path logPath = Paths.get("log_" + cfg[0] + ".txt");
            if (!Files.exists(logPath)) continue;
            try {
                for (String linea : Files.readAllLines(logPath)) {
                    if (linea.contains("Recibe ELECCION")) eleccion++;
                    if (linea.contains("Recibe OK")) ok++;
                    if (linea.contains("Nuevo COORDINADOR")) coordinador++;
                    if (linea.contains("RA_RECIBE solicitud")) raRequest++;
                    if (linea.contains("RA recibe REPLY")) raReply++;
                }
            } catch (IOException e) {}
        }

        long totalEleccion = eleccion + ok + coordinador;
        long totalExclusion = raRequest + raReply;
        System.out.println("\n=== MENSAJES DE COORDINACION (recibidos) ===");
        System.out.println("[Eleccion - Bully]");
        System.out.println("  ELECCION:    " + eleccion);
        System.out.println("  OK:          " + ok);
        System.out.println("  COORDINADOR: " + coordinador);
        System.out.println("  Subtotal:    " + totalEleccion);
        System.out.println("[Exclusion mutua - Ricart-Agrawala]");
        System.out.println("  REQUEST:     " + raRequest);
        System.out.println("  REPLY:       " + raReply);
        System.out.println("  Subtotal:    " + totalExclusion);
        System.out.println("TOTAL mensajes de coordinacion: " + (totalEleccion + totalExclusion));
        return new long[]{totalEleccion, totalExclusion};
    }

    private static LocalTime extraerHora(String linea) {
        try {
            int i = linea.indexOf("[Hora=") + 6;
            int j = linea.indexOf("]", i);
            return LocalTime.parse(linea.substring(i, j));
        } catch (Exception e) {
            return null;
        }
    }

    private static long[] medirRecuperacionSistema(String[][] nodosConfig, LocalTime horaFalla) {
        LocalTime deteccion = null;
        LocalTime reeleccion = null;

        for (String[] cfg : nodosConfig) {
            Path logPath = Paths.get("log_" + cfg[0] + ".txt");
            if (!Files.exists(logPath)) continue;
            try {
                for (String linea : Files.readAllLines(logPath)) {
                    LocalTime t = extraerHora(linea);
                    if (t == null || t.isBefore(horaFalla)) continue;
                    if (linea.contains("DETECTA CAIDA")) {
                        if (deteccion == null || t.isBefore(deteccion)) deteccion = t;
                    }
                    if (linea.contains("SOY COORDINADOR") || linea.contains("COORDINADOR establecido")) {
                        if (reeleccion == null || t.isBefore(reeleccion)) reeleccion = t;
                    }
                }
            } catch (IOException e) {}
        }

        long detMs = (deteccion != null) ? Duration.between(horaFalla, deteccion).toMillis() : -1;
        long reMs = (reeleccion != null) ? Duration.between(horaFalla, reeleccion).toMillis() : -1;

        System.out.println("\n=== RECUPERACION DEL SISTEMA (desde logs) ===");
        System.out.println(detMs >= 0
                ? "Deteccion de la caida: " + detMs + " ms tras la falla"
                : "No se registro deteccion de la caida en los logs");
        System.out.println(reMs >= 0
                ? "Nuevo coordinador electo: " + reMs + " ms tras la falla"
                : "No se registro re-eleccion tras la falla");
        return new long[]{detMs, reMs};
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
        AtomicLong tiempoFalla = new AtomicLong(0);

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
                    tiempoFalla, inicioGlobal, nodosInfo);
            clientes.add(c);
            pool.submit(c);
        }

        List<String> serie = Collections.synchronizedList(new ArrayList<>());
        serie.add("segundo,throughput_ops_s,errores_s");
        Thread muestreador = new Thread(() -> {
            long prevOps = 0, prevErr = 0;
            for (int s = 1; s <= duracionSegundos; s++) {
                try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
                long ops = exitoOpsNormales.get() + exitoOpsCaida.get()
                        + errorOpsNormales.get() + errorOpsCaida.get();
                long err = errorOpsNormales.get() + errorOpsCaida.get();
                serie.add(s + "," + (ops - prevOps) + "," + (err - prevErr));
                prevOps = ops;
                prevErr = err;
            }
        });
        muestreador.setDaemon(true);
        muestreador.start();

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
        LocalTime horaFalla = LocalTime.now();
        tiempoFalla.set(System.currentTimeMillis());
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

        long[] coord = contarMensajesCoordinacion(nodosConfig);

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

        long[] rec = medirRecuperacionSistema(nodosConfig, horaFalla);

        try {
            muestreador.join(2000);
        } catch (InterruptedException e) {

        }
        try (BufferedWriter w = Files.newBufferedWriter(Paths.get("metricas_tiempo.csv"))) {
            for (String linea : serie) { w.write(linea); w.newLine(); }
        } catch (IOException e) {
            System.out.println("No se pudo escribir metricas_tiempo.csv: " + e.getMessage());
        }

        try (BufferedWriter w = Files.newBufferedWriter(Paths.get("metricas_resumen.csv"))) {
            w.write("metrica,valor"); w.newLine();
            w.write("throughput_ops_s," + String.format(Locale.US, "%.2f", throughput)); w.newLine();
            w.write("exitos_normal," + totalExitosN); w.newLine();
            w.write("errores_normal," + totalErroresN); w.newLine();
            w.write("exitos_caida," + totalExitosC); w.newLine();
            w.write("errores_caida," + totalErroresC); w.newLine();
            w.write("latencia_p95_normal_ms," + percentil(latenciasNormales, 95)); w.newLine();
            w.write("latencia_p95_caida_ms," + percentil(latenciasCaida, 95)); w.newLine();
            w.write("msgs_coordinacion_eleccion," + coord[0]); w.newLine();
            w.write("msgs_coordinacion_exclusion," + coord[1]); w.newLine();
            w.write("deteccion_caida_ms," + rec[0]); w.newLine();
            w.write("reeleccion_ms," + rec[1]); w.newLine();
        } catch (IOException e) {
            System.out.println("No se pudo escribir metricas_resumen.csv: " + e.getMessage());
        }
        System.out.println("\nArchivos generados: metricas_tiempo.csv, metricas_resumen.csv");

        for (Process p : procesosNodos) {
            if (p.isAlive()) p.destroy();
        }
    }
}