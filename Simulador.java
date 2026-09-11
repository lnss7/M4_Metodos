import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * SIMULAÇÃO E MÉTODOS ANALÍTICOS - MÓDULO 4
 * Simulador de Eventos Discretos para Filas Simples (G/G/c/K)
 * Prof. Afonso Sales, Ph.D.
 */
public class Simulador {

    // Gerador Congruente Linear (LCG)
    static class LCG {
        private long a;
        private long c;
        private long M;
        private long previous;

        public LCG(long a, long c, long M, long seed) {
            this.a = a;
            this.c = c;
            this.M = M;
            this.previous = seed;
        }

        public double nextRandom() {
            this.previous = ((this.a * this.previous) + this.c) % this.M;
            return (double) this.previous / (double) this.M;
        }
    }

    enum TipoEvento {
        CHEGADA,
        SAIDA
    }

    static class Evento {
        double tempo;
        TipoEvento tipo;

        public Evento(double tempo, TipoEvento tipo) {
            this.tempo = tempo;
            this.tipo = tipo;
        }
    }

    static class ResultadoSimulacao {
        String config;
        String chegadas;
        String atendimento;
        double tempoGlobal;
        int perdas;
        int aleatoriosUsados;
        double[] times;
        double[] probabilidades;
    }

    public static ResultadoSimulacao simular(
            int servidores,
            int capacidade,
            double minChegada,
            double maxChegada,
            double minAtendimento,
            double maxAtendimento,
            double primeiraChegada,
            int limiteAleatorios,
            long seed
    ) {
        long a = 1664525L;
        long c_lcg = 1013904223L;
        long M = 4294967296L; // 2^32

        LCG gerador = new LCG(a, c_lcg, M, seed);
        int[] count = new int[]{limiteAleatorios};

        double tempoGlobal = 0.0;
        int fila = 0;
        int perdas = 0;
        double[] times = new double[capacidade + 1];
        List<Evento> escalonador = new ArrayList<>();

        // Função auxiliar para consumo de aleatório
        java.util.function.Supplier<Double> nextRandom = () -> {
            if (count[0] <= 0) return null;
            count[0]--;
            return gerador.nextRandom();
        };

        // Agenda evento mantendo ordenação por tempo
        java.util.function.BiConsumer<TipoEvento, Double> agendaEvento = (tipo, tempo) -> {
            escalonador.add(new Evento(tempo, tipo));
            escalonador.sort(Comparator.comparingDouble(e -> e.tempo));
        };

        // Agenda primeira chegada
        agendaEvento.accept(TipoEvento.CHEGADA, primeiraChegada);

        while (count[0] > 0 && !escalonador.isEmpty()) {
            Evento ev = escalonador.remove(0);

            // Contabiliza tempo no estado anterior
            double delta = ev.tempo - tempoGlobal;
            times[fila] += delta;
            tempoGlobal = ev.tempo;

            if (ev.tipo == TipoEvento.CHEGADA) {
                // Agenda próxima chegada
                if (count[0] > 0) {
                    Double rnd = nextRandom.get();
                    if (rnd != null) {
                        double proxChegada = tempoGlobal + (minChegada + (maxChegada - minChegada) * rnd);
                        agendaEvento.accept(TipoEvento.CHEGADA, proxChegada);
                    }
                }

                // Trata capacidade
                if (fila < capacidade) {
                    fila++;
                    if (fila <= servidores) {
                        if (count[0] > 0) {
                            Double rnd = nextRandom.get();
                            if (rnd != null) {
                                double tempoSaida = tempoGlobal + (minAtendimento + (maxAtendimento - minAtendimento) * rnd);
                                agendaEvento.accept(TipoEvento.SAIDA, tempoSaida);
                            }
                        }
                    }
                } else {
                    perdas++;
                }
            } else if (ev.tipo == TipoEvento.SAIDA) {
                fila--;
                if (fila >= servidores) {
                    if (count[0] > 0) {
                        Double rnd = nextRandom.get();
                        if (rnd != null) {
                            double tempoSaida = tempoGlobal + (minAtendimento + (maxAtendimento - minAtendimento) * rnd);
                            agendaEvento.accept(TipoEvento.SAIDA, tempoSaida);
                        }
                    }
                }
            }
        }

        ResultadoSimulacao res = new ResultadoSimulacao();
        res.config = "G/G/" + servidores + "/" + capacidade;
        res.chegadas = "[" + minChegada + ".." + maxChegada + "]";
        res.atendimento = "[" + minAtendimento + ".." + maxAtendimento + "]";
        res.tempoGlobal = tempoGlobal;
        res.perdas = perdas;
        res.aleatoriosUsados = limiteAleatorios - count[0];
        res.times = times;
        res.probabilidades = new double[capacidade + 1];
        for (int i = 0; i <= capacidade; i++) {
            res.probabilidades[i] = tempoGlobal > 0 ? times[i] / tempoGlobal : 0.0;
        }

        return res;
    }

    public static void imprimirRelatorio(ResultadoSimulacao res) {
        System.out.println("*********************************************************");
        System.out.println("Queue:   Q1 (" + res.config + ")");
        System.out.println("Arrival: " + res.chegadas.replace("[", "").replace("]", "").replace("..", " ... "));
        System.out.println("Service: " + res.atendimento.replace("[", "").replace("]", "").replace("..", " ... "));
        System.out.println("*********************************************************");
        System.out.printf("%8s%19s%26s%n", "State", "Time", "Probability");
        for (int i = 0; i < res.times.length; i++) {
            System.out.printf("%7d%21.4f%21.2f%%%n", i, res.times[i], res.probabilidades[i] * 100.0);
        }
        System.out.println();
        System.out.printf("Number of losses: %d%n%n", res.perdas);
        System.out.println("=========================================================");
        System.out.printf("Simulation average time: %.4f%n", res.tempoGlobal);
        System.out.println("=========================================================");
        System.out.println();
    }

    public static void main(String[] args) {
        // Cenário 1: G/G/1/5 (Chegadas: 3.0 ... 5.0, Atendimento: 4.0 ... 5.0)
        ResultadoSimulacao res1 = simular(1, 5, 3.0, 5.0, 4.0, 5.0, 3.0, 100000, 1L);
        imprimirRelatorio(res1);

        // Cenário 2: G/G/2/5 (Chegadas: 3.0 ... 5.0, Atendimento: 4.0 ... 5.0)
        ResultadoSimulacao res2 = simular(2, 5, 3.0, 5.0, 4.0, 5.0, 3.0, 100000, 1L);
        imprimirRelatorio(res2);
    }
}
