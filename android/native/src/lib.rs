#[cfg(all(target_os = "android", not(target_arch = "aarch64")))]
compile_error!("The Android port supports ARM64 only");

#[cfg(target_os = "android")]
use bevy::ecs as bevy_ecs;

#[cfg(target_os = "android")]
use bevy::{
    prelude::*,
    core_pipeline::tonemapping::Tonemapping,
    render::{RenderPlugin, settings::{Backends, RenderCreation, WgpuSettings}},
    window::{AppLifecycle, WindowMode},
};

#[cfg(target_os = "android")]
#[bevy_main]
fn main() {
    skate_game::android_ui::STATE.begin();
    // RenderPlugin initializes Vulkan while plugins are added, so install
    // diagnostics before that stage can panic.
    std::panic::set_hook(Box::new(|panic| {
        skate_game::android_ui::STATE.fail();
        bevy::log::error!("SKATE_ANDROID panic: {panic}");
    }));
    let mut logging = App::new();
    logging.add_plugins(bevy::log::LogPlugin {
        filter: "info,wgpu_core=warn,wgpu_hal=warn,naga=warn".into(),
        custom_layer: file_diagnostics,
        ..default()
    });
    if let Some(root) = bevy::android::ANDROID_APP.get().and_then(|app| app.internal_data_path()) {
        let installation = root.join("installation");
        if installation.join("complete.json").is_file() {
            match skate_game::run_installed_android(installation) {
                Ok(mut game) => { game.run(); return; }
                Err(error) => {skate_game::android_ui::STATE.fail();error!("SKATE_ANDROID gameplay startup failed: {error}");},
            }
        } else {
            skate_game::android_ui::STATE.fail();
            info!("SKATE_ANDROID no complete installation; starting diagnostic scene");
        }
    }
    skate_game::android_ui::STATE.fail();
    let mut app = App::new();
    app.add_plugins(DefaultPlugins
        .set(bevy::log::LogPlugin {
            filter: "info,wgpu_core=warn,wgpu_hal=warn,naga=warn".into(),
            custom_layer: file_diagnostics,
            ..default()
        })
        .set(WindowPlugin {
            primary_window: Some(Window {
                title: "skate3rust Android Runtime".into(),
                mode: WindowMode::BorderlessFullscreen(MonitorSelection::Primary),
                ..default()
            }),
            ..default()
        })
        .set(RenderPlugin {
            render_creation: RenderCreation::Automatic(WgpuSettings {
                backends: Some(Backends::VULKAN),
                ..default()
            }),
            ..default()
        }).build().disable::<bevy::log::LogPlugin>())
        .insert_resource(ClearColor(Color::srgb(0.025, 0.06, 0.12)))
        .insert_resource(bevy::winit::WinitSettings {
            focused_mode: bevy::winit::UpdateMode::Continuous,
            ..bevy::winit::WinitSettings::mobile()
        })
        .add_systems(Startup, setup)
        .add_systems(Update, (rotate, lifecycle));
    app.run();
}

#[cfg(target_os="android")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_skate3rust_android_StartupStatus_stage(_: *mut std::ffi::c_void, _: *mut std::ffi::c_void) -> i32 {
    i32::from(skate_game::android_ui::STATE.stage())
}

#[cfg(target_os="android")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_skate3rust_android_StartupStatus_takeLayoutRequest(_: *mut std::ffi::c_void, _: *mut std::ffi::c_void) -> u8 {
    u8::from(skate_game::android_ui::STATE.take_layout_request())
}

#[cfg(target_os = "android")]
fn file_diagnostics(_: &mut App) -> Option<bevy::log::BoxedLayer> {
    use bevy::log::tracing_subscriber::{fmt, Layer};
    let directory = bevy::android::ANDROID_APP.get()?.internal_data_path()?;
    let file = std::fs::File::create(directory.join("runtime.log")).ok()?;
    Some(fmt::layer().with_ansi(false).with_writer(std::sync::Mutex::new(file)).boxed())
}

#[cfg(target_os = "android")]
#[derive(Component)]
struct DiagnosticCube;

#[cfg(target_os = "android")]
fn setup(mut commands: Commands, mut meshes: ResMut<Assets<Mesh>>, mut materials: ResMut<Assets<StandardMaterial>>) {
    info!("SKATE_ANDROID runtime initialized; ABI=arm64-v8a; backend=Vulkan; mode=diagnostic; assets=none");
    commands.spawn((Mesh3d(meshes.add(Plane3d::default().mesh().size(8.0, 8.0))), MeshMaterial3d(materials.add(Color::srgb(0.12, 0.17, 0.2)))));
    commands.spawn((DiagnosticCube, Mesh3d(meshes.add(Cuboid::new(1.4, 1.4, 1.4))), MeshMaterial3d(materials.add(Color::srgb(1.0, 0.35, 0.04))), Transform::from_xyz(0.0, 1.0, 0.0)));
    commands.spawn((PointLight { intensity: 1_000_000.0, shadows_enabled: false, ..default() }, Transform::from_xyz(3.0, 6.0, 4.0)));
    commands.spawn((Camera3d::default(), Msaa::Off, Tonemapping::None, Transform::from_xyz(-3.0, 3.0, 6.0).looking_at(Vec3::new(0.0, 0.6, 0.0), Vec3::Y)));
}

#[cfg(target_os = "android")]
fn rotate(time: Res<Time>, mut cubes: Query<&mut Transform, With<DiagnosticCube>>) {
    for mut transform in &mut cubes { transform.rotate_y(time.delta_secs() * 0.6); }
}

#[cfg(target_os = "android")]
fn lifecycle(mut events: MessageReader<AppLifecycle>) {
    for event in events.read() { info!("SKATE_ANDROID lifecycle={event:?}"); }
}

#[cfg(target_os = "android")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_skate3rust_android_GameActivity_controller(
    _: *mut std::ffi::c_void, _: *mut std::ffi::c_void,
    lx: f32, ly: f32, rx: f32, ry: f32, lt: f32, rt: f32, buttons: i32,
) { skate_game::controller_update([lx,ly,rx,ry,lt,rt], buttons as u16); }

#[cfg(target_os = "android")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_skate3rust_android_GameActivity_clearInput(
    _: *mut std::ffi::c_void, _: *mut std::ffi::c_void,
) { skate_game::controller_clear(); }

#[cfg(target_os = "android")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_skate3rust_android_GameActivity_audioActive(
    _: *mut std::ffi::c_void, _: *mut std::ffi::c_void, active: u8,
) { skate_game::audio_active(active != 0); }
