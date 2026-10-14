#[path = "../../crates/skate-game/src/android_ui.rs"]
mod ui;
#[test]
fn readiness_and_failure_are_real_and_requests_are_consumed_once() {
    let state = ui::UiState::new();
    assert_eq!(state.stage(), 0);
    state.ready(); assert_eq!(state.stage(), 0);
    state.set_stage(4); state.ready(); assert_eq!(state.stage(), 5);
    state.request_layout(); assert!(state.take_layout_request()); assert!(!state.take_layout_request());
    state.fail(); state.ready(); assert_eq!(state.stage(), 255);
    state.set_stage(4); assert_eq!(state.stage(), 255);
    state.begin(); assert_eq!(state.stage(), 0);
    state.set_stage(4);let mut frames=0;
    for _ in 0..5 {state.frame(false,false,&mut frames);}
    assert_eq!(state.stage(),4);
    state.frame(true,false,&mut frames);assert_eq!(state.stage(),4);
    state.frame(true,false,&mut frames);assert_eq!(state.stage(),5);
    state.begin();state.set_stage(4);state.frame(false,true,&mut frames);assert_eq!(state.stage(),255);
}
