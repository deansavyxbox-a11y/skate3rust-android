use std::sync::Mutex;

#[derive(Clone, Copy, Default, Debug, PartialEq)]
pub(crate) struct AndroidControllerState {
    pub connected: bool,
    pub number: u32,
    pub buttons: u16,
    pub triggers: [u8; 2],
    pub left: [i16; 2],
    pub right: [i16; 2],
}

impl AndroidControllerState {
    fn update_when_active(&mut self, axes: [f32;6], buttons: u16, active: bool) {
        if active { self.update(axes,buttons); } else { self.clear(); }
    }
    fn update(&mut self, axes: [f32; 6], buttons: u16) {
        fn finite(value: f32) -> f32 { if value.is_finite() { value } else { 0. } }
        fn axis(value: f32) -> i16 {
            let value = finite(value).clamp(-1., 1.);
            (value * if value < 0. { 32768. } else { 32767. }).round() as i16
        }
        self.connected = true;
        self.number = self.number.wrapping_add(1);
        self.buttons = buttons;
        self.left = [axis(axes[0]), axis(-axes[1])];
        self.right = [axis(axes[2]), axis(-axes[3])];
        self.triggers = [axes[4], axes[5]].map(|v| (finite(v).clamp(0.,1.) * 255.).round() as u8);
    }
    fn clear(&mut self) { *self = Self { number: self.number.wrapping_add(1), ..Self::default() }; }
}
static STATE: Mutex<AndroidControllerState> = Mutex::new(AndroidControllerState {
    connected: false, number: 0, buttons: 0, triggers: [0; 2], left: [0; 2], right: [0; 2],
});
pub(crate) fn update(axes: [f32; 6], buttons: u16, active: bool) { STATE.lock().unwrap_or_else(|e| e.into_inner()).update_when_active(axes, buttons, active); }
pub(crate) fn clear() { STATE.lock().unwrap_or_else(|e| e.into_inner()).clear(); }
pub(crate) fn sample() -> AndroidControllerState { *STATE.lock().unwrap_or_else(|e| e.into_inner()) }

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn repeated_inactive_controller_events_cannot_restore_held_actions() {
        let mut state=AndroidControllerState::default();
        state.update_when_active([1.;6],0x1000,true);
        assert!(state.connected);
        for _ in 0..3 {
            state.update_when_active([1.;6],0x1000,false);
            assert!(!state.connected);
            assert_eq!(state.buttons,0);
            assert_eq!(state.left,[0,0]);
        }
        state.update_when_active([0.;6],0,true);
        assert!(state.connected);
        assert_eq!(state.buttons,0);
    }
    #[test]
    fn maps_axes_triggers_buttons_and_clears_disconnected_state() {
        let mut state = AndroidControllerState::default();
        state.update([-1., -1., 1., 1., 0., 1.], 0x1010);
        assert!(state.connected);
        assert_eq!(state.left, [i16::MIN, i16::MAX]);
        assert_eq!(state.right, [i16::MAX, i16::MIN]);
        assert_eq!(state.triggers, [0, 255]);
        assert_eq!(state.buttons, 0x1010);
        let old = state.number;
        state.update([f32::NAN, f32::INFINITY, -2., 2., -1., 2.], 0);
        assert_eq!(state.left, [0, 0]);
        assert_eq!(state.right, [i16::MIN, i16::MIN]);
        assert_eq!(state.triggers, [0, 255]);
        assert_ne!(state.number, old);
        state.clear();
        assert!(!state.connected);
        assert_eq!(state.buttons, 0);
        assert_eq!(state.left, [0, 0]);
        assert_eq!(state.triggers, [0, 0]);
    }
}
