use bevy::prelude::*;
use std::path::PathBuf;

/// Explicit Android startup; desktop updater/setup/supervisor never run here.
pub fn run_android(asset_root: PathBuf, map_path: PathBuf) -> Result<App, String> {
    let root = asset_root.parent().ok_or("Assets need an installation parent")?;
    let relative = map_path.strip_prefix(root).map_err(|_| "Map is outside installation")?;
    let (asset_root, map_path) = crate::android_paths::resolve_installation(root, relative)?;
    build_game(asset_root,Some(map_path))
}

pub fn run_installed_android(root: PathBuf) -> Result<App,String> {
    use crate::android_paths::MapChoice;
    let pointer=root.join("settings/default-map.json");
    let stored=std::fs::read(&pointer).ok().map(|bytes|serde_json::from_slice::<Option<String>>(&bytes));
    let choice=match &stored {
        Some(Ok(Some(name)))=>MapChoice::Saved(name),
        Some(Ok(None))=>MapChoice::TestWorld,
        _=>MapChoice::Default,
    };
    let (assets,map)=crate::android_paths::resolve_startup(&root,choice)?;
    let names:Vec<_>=crate::map_library::discover(&assets).into_iter().map(|m|m.label).collect();
    info!("SKATE_ANDROID map_catalog={names:?}");
    build_game(assets,map)
}

fn build_game(asset_root: PathBuf, map_path: Option<PathBuf>) -> Result<App,String> {
    crate::android_ui::STATE.set_stage(1);
    info!("SKATE_ANDROID stage=configuration map={}",map_path.as_ref().map_or("Test world".into(),|p|p.display().to_string()));
    let difficulty = crate::difficulty::Difficulty::load(&asset_root)?;
    let (map,fingerprint)=if let Some(path)=map_path.as_deref() {
        let (map,fp)=crate::map_library::load_map(&asset_root,path)?;
        info!("SKATE_ANDROID prepared_map={:?} render_triangles={} rails={} materials={}",map.name,map.geometry.indices.len()/3,map.rails.len(),map.materials.len());
        (Some(map),fp)
    } else {(None,crate::config::map_fingerprint(None)?)};
    if let Some(map)=&map { crate::skate_world::validate_runtime(map)?; }
    let config = crate::config::Config {
        map_fingerprint: fingerprint,
        asset_root, map, map_path, difficulty,
        verification_capture: None, check_assets: false, start_paused: false,
        multiplayer: crate::multiplayer::Options::default(), teleport: None,
    };
    info!("SKATE_ANDROID stage=gameplay_configuration");
    skate_data::input_config::StockGameplayConfig::load(&config.asset_root).map_err(|e| e.to_string())?;
    let manifest = skate_data::GameAssets::load(&config.asset_root).map_err(|e| e.to_string())?;
    info!("SKATE_ANDROID stage=stock_graphs");
    crate::android_ui::STATE.set_stage(2);
    let graphs = crate::graph_runtime::StockGraphs::load(&config.asset_root, &manifest)?;
    info!("SKATE_ANDROID stage=physics");
    crate::android_ui::STATE.set_stage(3);
    let physics = crate::physics::GamePhysics::load_with_difficulty(&config.asset_root, config.map.as_ref(), config.difficulty)?;
    let skater = crate::physics::SkaterRuntime::load(&config.asset_root, &graphs, &physics, config.difficulty.profile_key())?;
    let controls = crate::physics::PlayerControls::load(&config.asset_root)?;
    info!("SKATE_ANDROID stage=renderer_and_app");
    crate::android_ui::STATE.set_stage(4);
    let mut app = crate::app::build(config, manifest, graphs, physics, skater);
    app.insert_resource(controls);
    app.add_systems(Update, (lifecycle, focus));
    app.add_systems(Last, startup_ready);
    Ok(app)
}

fn startup_ready(assets:Res<crate::assets::AssetStatus>, mut frames: Local<u8>) {
    crate::android_ui::STATE.frame(*assets==crate::assets::AssetStatus::Ready,*assets==crate::assets::AssetStatus::Failed,&mut frames);
}

fn lifecycle(mut events: MessageReader<bevy::window::AppLifecycle>) {
    for event in events.read() {
        info!("SKATE_ANDROID lifecycle={event:?}");
        if matches!(event, bevy::window::AppLifecycle::Suspended) { controller_clear(); }
    }
}

pub fn controller_update(axes: [f32; 6], buttons: u16) { crate::input::android::update(axes, buttons, is_active()); }
pub fn controller_clear() { crate::input::android::clear(); }

static ACTIVE: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(true);
pub(crate) fn is_active() -> bool { ACTIVE.load(std::sync::atomic::Ordering::Relaxed) }
pub fn audio_active(active: bool) {
    ACTIVE.store(active, std::sync::atomic::Ordering::Relaxed);
    if !active { controller_clear(); }
}

fn focus(sinks: Query<(Entity, &AudioSink), Without<crate::modding::ModAudioVoice>>,
    spatial: Query<(Entity, &SpatialAudioSink), Without<crate::modding::ModAudioVoice>>,
    mut paused: Local<std::collections::HashSet<Entity>>, mut prior: Local<Option<bool>>) {
    use bevy::audio::AudioSinkPlayback;
    let active = is_active();
        for (id,sink) in &sinks {
            if !active && !sink.is_paused() { sink.pause(); paused.insert(id); }
            else if active && paused.remove(&id) { sink.play(); }
        }
        for (id,sink) in &spatial {
            if !active && !sink.is_paused() { sink.pause(); paused.insert(id); }
            else if active && paused.remove(&id) { sink.play(); }
        }
    if *prior != Some(active) {
        info!("SKATE_ANDROID audio_focus={active}");
        *prior = Some(active);
    }
}
