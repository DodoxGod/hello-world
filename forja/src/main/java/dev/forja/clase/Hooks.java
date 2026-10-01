package dev.forja.clase;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * The names of the tree nodes that do something a number cannot say (docs/ARBOLES.md): each one is a node's
 * {@code gancho} in forja_arboles.json, and the code that does it asks {@link ClassEffects#hook} for it by
 * this name and reads the node's numbers from there. A test (ClasesGameTests.ganchos) checks both ways: every
 * name here is a node of some tree, and every node with a text has its name here.
 */
public final class Hooks {
	// Guerrero
	public static final String SEGUNDO_ALIENTO = "segundo_aliento";
	public static final String INQUEBRANTABLE = "inquebrantable";
	public static final String ADRENALINA = "adrenalina";
	public static final String REPLICA = "replica";
	public static final String FORTALEZA = "fortaleza";
	public static final String FILO_DE_VUELTA = "filo_de_vuelta";
	public static final String DUELISTA = "duelista";
	public static final String MARTILLO_DE_GUERRA = "martillo_de_guerra";
	public static final String SED_DE_SANGRE = "sed_de_sangre";
	public static final String CARGA_BRUTAL = "carga_brutal";
	// Asesino
	public static final String FILO_DEL_VIENTO = "filo_del_viento";
	public static final String ESPEJISMO = "espejismo";
	public static final String SIN_SOMBRA = "sin_sombra";
	public static final String GOLPE_DE_GRACIA = "golpe_de_gracia";
	public static final String GOLPE_LETAL = "golpe_letal";
	public static final String FRENESI = "frenesi";
	public static final String PASO_QUEDO = "paso_quedo";
	public static final String FUNAMBULO = "funambulo";
	public static final String EVASION = "evasion";
	public static final String FANTASMA = "fantasma";
	public static final String VENENO_EN_LA_HOJA = "veneno_en_la_hoja";
	// Tanque
	public static final String REPRESALIA = "represalia";
	public static final String ESPINAS_DE_ACERO = "espinas_de_acero";
	public static final String MURALLA_VIVA = "muralla_viva";
	public static final String RECUPERACION = "recuperacion";
	public static final String ULTIMO_BASTION = "ultimo_bastion";
	public static final String DESAFIO = "desafio";
	public static final String IMAN_DE_GOLPES = "iman_de_golpes";
	public static final String PISOTON = "pisoton";
	// Mago
	public static final String CATALIZADOR = "catalizador";
	public static final String HECHIZO_ENCADENADO = "hechizo_encadenado";
	public static final String CARGA_PROFUNDA = "carga_profunda";
	public static final String TODO_O_NADA = "todo_o_nada";
	public static final String POZO_SIN_FONDO = "pozo_sin_fondo";
	public static final String SANGRE_POR_MANA = "sangre_por_mana";
	public static final String PARPADEO = "parpadeo";
	public static final String EGIDA = "egida";
	public static final String ESCUDO_DE_MANA = "escudo_de_mana";
	public static final String RUNA_DE_ESCARCHA = "runa_de_escarcha";
	// Curandero
	public static final String MILAGRO = "milagro";
	public static final String MARTIR = "martir";
	public static final String RENUEVO = "renuevo";
	public static final String FLORECER = "florecer";
	public static final String BENDICION = "bendicion";
	public static final String PURIFICAR = "purificar";
	public static final String TIERRA_SAGRADA = "tierra_sagrada";
	public static final String VINCULO = "vinculo";
	public static final String LAZO_VITAL = "lazo_vital";
	public static final String AURA = "aura";
	public static final String PEREGRINO = "peregrino";
	public static final String MARTILLO_DE_LA_FE = "martillo_de_la_fe";
	public static final String ROCIO = "rocio";
	// Arquero
	public static final String FRANCOTIRADOR = "francotirador";
	public static final String TIRO_LEJANO = "tiro_lejano";
	public static final String OJO_DE_AGUILA = "ojo_de_aguila";
	public static final String RAFAGA = "rafaga";
	public static final String TIRO_CERTERO = "tiro_certero";
	public static final String FLECHA_PERFORANTE = "flecha_perforante";
	public static final String DISPARO_EN_CARRERA = "disparo_en_carrera";
	public static final String HALCON = "halcon";
	public static final String MARCA_DEL_CAZADOR = "marca_del_cazador";
	// Bridges
	public static final String REPLICA_MENOR = "replica_menor";
	public static final String REPRESALIA_MENOR = "represalia_menor";
	public static final String VENDAJE = "vendaje";
	// The forge
	public static final String FUELLE = "fuelle";
	public static final String AJUSTE_FINO = "ajuste_fino";
	public static final String TEMPLE_DE_CAMPANA = "temple_de_campana";
	public static final String TEMPLE_DE_CAMPANA_II = "temple_de_campana_ii";
	public static final String FORJA_AL_ROJO = "forja_al_rojo";

	private Hooks() {
	}

	/** Every name above, for the test. */
	public static List<String> all() {
		List<String> out = new ArrayList<>();
		for (Field field : Hooks.class.getFields()) {
			if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
				try {
					out.add((String) field.get(null));
				} catch (IllegalAccessException e) {
					throw new IllegalStateException(e);
				}
			}
		}
		return out;
	}
}
