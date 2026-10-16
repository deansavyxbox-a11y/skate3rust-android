//! Android defaults for known full-park packages; imported bytes stay immutable.

pub(crate) fn correct_park_start(name:&str,supplement_fingerprint:u64,spawn:&mut [f32;3]) {
    // Both the PC-prepared and on-phone ISO exports of the verified full layout.
    // Its authored locator lies below the 1.45 m platform, inside the park floor.
    if name=="DownTownSkatePark"
        && matches!(supplement_fingerprint,875047220466072805|10763177649548943994)
        && spawn.iter().zip([66.052162,0.,-64.023094]).all(|(a,b)|(*a-b).abs()<0.001)
    {
        *spawn=[80.,1.45,-70.];
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn downtown_default_moves_above_full_park_platform() {
        for fingerprint in [875047220466072805,10763177649548943994] {
            let mut spawn=[66.052162,0.,-64.023094];
            correct_park_start("DownTownSkatePark",fingerprint,&mut spawn);
            assert_eq!(spawn,[80.,1.45,-70.]);
        }
    }
    #[test]
    fn unknown_packages_other_maps_and_changed_starts_are_preserved() {
        for (name,fp,original) in [
            ("DownTownSkatePark",1,[66.052162,0.,-64.023094]),
            ("MegaPark",875047220466072805,[66.052162,0.,-64.023094]),
            ("DownTownSkatePark",875047220466072805,[10.,2.,30.]),
        ] {
            let mut spawn=original;correct_park_start(name,fp,&mut spawn);assert_eq!(spawn,original);
        }
    }
}
