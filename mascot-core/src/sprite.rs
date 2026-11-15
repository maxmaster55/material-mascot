//! Small props drawn beside the mascot: Zzz, hearts, question marks.
//! Stored as tiny bitmaps; the smooth looks trace them into rounded glyphs.

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub(crate) enum Sprite {
    Z,
    Zs,
    Q,
    Ex,
    Heart,
    HeartS,
    Spark,
    Plus,
    Tear,
    Anger,
    AngerS,
    Book,
    Badge,
    BadgeL,
    Dot,
    Px,
    Arc1,
    Arc2,
    Arc1L,
    Arc2L,
    Bub0,
    Bub1,
    Bub2,
    Bub3,
}

impl Sprite {
    /// Rows of the bitmap: `#` is the prop's colour, `o` the dark tone, `.` empty.
    pub fn rows(self) -> &'static [&'static [u8]] {
        match self {
            Sprite::Z => &[b"####", b"..#.", b".#..", b"####"],
            Sprite::Zs => &[b"###", b".#.", b"###"],
            Sprite::Q => &[b".##.", b"#..#", b"..#.", b".#..", b"....", b".#.."],
            Sprite::Ex => &[b"##", b"##", b"##", b"##", b"..", b"##"],
            Sprite::Heart => &[b".#.#.", b"#####", b"#####", b".###.", b"..#.."],
            Sprite::HeartS => &[b"#.#", b"###", b".#."],
            Sprite::Spark => &[b"..#..", b"..#..", b"##.##", b"..#..", b"..#.."],
            Sprite::Plus => &[b".#.", b"###", b".#."],
            Sprite::Tear => &[b".#.", b"###", b"###"],
            Sprite::Anger => &[b".#.#.", b"##.##", b".....", b"##.##", b".#.#."],
            Sprite::AngerS => &[b"#.#", b"...", b"#.#"],
            Sprite::Book => &[b"####.####", b"#...#...#", b"#...#...#", b"#########"],
            Sprite::Badge => &[b".###.", b"#####", b"#####", b"#####", b".###."],
            Sprite::BadgeL => &[
                b"..###..", b".#####.", b"#######", b"#######", b"#######", b".#####.", b"..###..",
            ],
            Sprite::Dot => &[b"##", b"##"],
            Sprite::Px => &[b"#"],
            Sprite::Arc1 => &[b"#.", b".#", b"#."],
            Sprite::Arc2 => &[b"#.", b".#", b".#", b".#", b"#."],
            Sprite::Arc1L => &[b".#", b"#.", b".#"],
            Sprite::Arc2L => &[b".#", b"#.", b"#.", b"#.", b".#"],
            Sprite::Bub0 => &[
                b".#######.", b"##o######", b"####o#o##", b"#########", b".#######.", b"..##.....", b".#.......",
            ],
            Sprite::Bub1 => &[
                b".#######.", b"####o####", b"##o###o##", b"#########", b".#######.", b"..##.....", b".#.......",
            ],
            Sprite::Bub2 => &[
                b".#######.", b"######o##", b"##o#o####", b"#########", b".#######.", b"..##.....", b".#.......",
            ],
            Sprite::Bub3 => &[
                b".#######.", b"#########", b"##o#o#o##", b"#########", b".#######.", b"..##.....", b".#.......",
            ],
        }
    }

    /// The cell at (row, col), or `.` outside the bitmap.
    #[inline]
    pub fn cell(self, r: i32, q: i32) -> u8 {
        let rows = self.rows();
        if r < 0 || q < 0 || r as usize >= rows.len() {
            return b'.';
        }
        let row = rows[r as usize];
        if q as usize >= row.len() { b'.' } else { row[q as usize] }
    }
}
