package Carga;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class GeneradorCarga {
    public static void main(String[] args) throws InterruptedException {
        int numClientes = 50;
        long duracionSegundos = 60;
        long inicioGlobal = System.currentTimeMillis();

        List<String> usuarios = new ArrayList<>();
        for (int i = 0; i < numClientes; i++) {
            usuarios.add("carga" + i);
        }

        AtomicLong exitoOps = new AtomicLong(0);
        AtomicLong errorOps = new AtomicLong(0);
        List<Long> latencias = Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(numClientes);
        List<CargaCliente> clientes = new ArrayList<>();

        for (String user : usuarios) {
            CargaCliente c = new CargaCliente(user, duracionSegundos, usuarios, exitoOps, errorOps, latencias, inicioGlobal);
            clientes.add(c);
            pool.submit(c);
        }

        pool.shutdown();
        boolean terminado = pool.awaitTermination(duracionSegundos + 30, TimeUnit.SECONDS);
        if (!terminado) pool.shutdownNow();

        // Estadísticas
        long totalExitos = exitoOps.get();
        long totalErrores = errorOps.get();
        long totalOps = totalExitos + totalErrores;

        System.out.println("\n=== RESULTADOS DE CARGA ===");
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
    }
}
