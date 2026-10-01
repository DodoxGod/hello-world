package dev.forja.ai;

import java.util.List;

/**
 * The names of the inputs, in order, exactly as the simulator writes them into a network file
 * ("nombres_obs"). A network whose names do not match these, in this order, is not used: the rules
 * decide instead and the log says why. Copied from red_mob_v1 (motor_rust, lote_mobs.rs NOMBRES_OBS_MOB).
 */
public final class ObsNames {
	public static final List<String> M1 = List.of(
		"obj_distancia/16", "obj_dy/4", "obj_en_alcance", "obj_vel_hacia_mi*5",
		"obj_vel_lateral*5", "obj_vy*5", "obj_en_suelo", "obj_agachado",
		"obj_esprintando", "obj_escudo_arriba", "obj_escudo_activo", "obj_mano_espada",
		"obj_mano_hacha", "obj_mano_arco", "obj_mano_comida", "obj_mano_bloques",
		"obj_mano_nada", "obj_vida/20", "obj_me_mira(cos)", "obj_desde_ataque/40",
		"obj_comiendo", "obj_arco_tensado/20", "yo_vida_frac", "yo_en_suelo",
		"yo_vel_hacia_obj*5", "yo_vel_lateral*5", "yo_vy*5", "yo_recarga/40",
		"yo_arco/20", "yo_mecha/30", "yo_fuego", "yo_en_agua",
		"yo_herido", "tipo_zombie", "tipo_husk", "tipo_drowned",
		"tipo_skeleton", "tipo_stray", "tipo_creeper", "tipo_spider",
		"yo_bebe", "yo_ve_obj", "yo_tridente", "altura_r1.5_adelante",
		"altura_r1.5_adelante_derecha", "altura_r1.5_derecha", "altura_r1.5_atras_derecha", "altura_r1.5_atras",
		"altura_r1.5_atras_izquierda", "altura_r1.5_izquierda", "altura_r1.5_adelante_izquierda", "altura_r3_adelante",
		"altura_r3_adelante_derecha", "altura_r3_derecha", "altura_r3_atras_derecha", "altura_r3_atras",
		"altura_r3_atras_izquierda", "altura_r3_izquierda", "altura_r3_adelante_izquierda", "peligro_r1.5_adelante",
		"peligro_r1.5_adelante_derecha", "peligro_r1.5_derecha", "peligro_r1.5_atras_derecha", "peligro_r1.5_atras",
		"peligro_r1.5_atras_izquierda", "peligro_r1.5_izquierda", "peligro_r1.5_adelante_izquierda", "agua_cerca",
		"aliado0_presente", "aliado0_delante/16", "aliado0_derecha/16", "aliado0_dy/4",
		"aliado0_dist_obj/16", "aliado0_g_cuerpo", "aliado0_g_arquero", "aliado0_g_creeper",
		"aliado0_g_arana", "aliado0_g_otro", "aliado0_vida_frac", "aliado1_presente",
		"aliado1_delante/16", "aliado1_derecha/16", "aliado1_dy/4", "aliado1_dist_obj/16",
		"aliado1_g_cuerpo", "aliado1_g_arquero", "aliado1_g_creeper", "aliado1_g_arana",
		"aliado1_g_otro", "aliado1_vida_frac", "aliado2_presente", "aliado2_delante/16",
		"aliado2_derecha/16", "aliado2_dy/4", "aliado2_dist_obj/16", "aliado2_g_cuerpo",
		"aliado2_g_arquero", "aliado2_g_creeper", "aliado2_g_arana", "aliado2_g_otro",
		"aliado2_vida_frac", "n_aliados/5"
	);

	private ObsNames() {
	}
}
