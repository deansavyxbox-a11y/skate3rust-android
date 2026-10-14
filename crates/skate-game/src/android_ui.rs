use std::sync::atomic::{AtomicBool, AtomicU8, Ordering};

/// Small Android UI bridge. Readiness is set by completed gameplay updates.
pub struct UiState { stage: AtomicU8, edit_layout: AtomicBool }
impl UiState {
    pub const fn new() -> Self { Self {stage: AtomicU8::new(0), edit_layout: AtomicBool::new(false)} }
    pub fn begin(&self) {self.stage.store(0,Ordering::Release); self.edit_layout.store(false,Ordering::Release);}
    pub fn stage(&self) -> u8 {self.stage.load(Ordering::Acquire)}
    pub fn set_stage(&self, stage:u8) {
        let _=self.stage.fetch_update(Ordering::AcqRel,Ordering::Acquire,|previous| {
            (previous!=255 && previous!=5 && stage<=4).then_some(stage)
        });
    }
    pub fn ready(&self) {let _=self.stage.compare_exchange(4,5,Ordering::AcqRel,Ordering::Acquire);}
    pub fn fail(&self) {self.stage.store(255,Ordering::Release);}
    pub fn frame(&self, assets_ready:bool, assets_failed:bool, frames:&mut u8) {
        if assets_failed {*frames=0;self.fail();}
        else if assets_ready {*frames=frames.saturating_add(1);if *frames>=2 {self.ready();}}
        else {*frames=0;}
    }
    pub fn request_layout(&self) {self.edit_layout.store(true,Ordering::Release);}
    pub fn take_layout_request(&self) -> bool {self.edit_layout.swap(false,Ordering::AcqRel)}
}
pub static STATE: UiState=UiState::new();
