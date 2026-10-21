from pathlib import Path

OUT = Path("mockups/screens")
OUT.mkdir(parents=True, exist_ok=True)

CSS = r'''
* { box-sizing: border-box; }
html, body { margin: 0; width: 448px; height: 938px; }
body {
  font-family: "Noto Sans", "Noto Sans Arabic", sans-serif;
  background: #F7F4ED; color: #14201C;
  font-feature-settings: "tnum" 1, "kern" 1;
  -webkit-font-smoothing: antialiased;
}
:root {
  --bg:#F7F4ED; --surface:#FFFFFF; --surface-alt:#F1ECE2;
  --outline:#DCD5C7; --ink:#14201C; --muted:#64736E;
  --primary:#0E6B5C; --on-primary:#FFFFFF; --accent:#C97B2E;
  --success:#2E7D5B; --danger:#B23B36;
  --c1:#0E6B5C; --c2:#C97B2E; --c3:#6C8CD5; --c4:#B65F82;
  --c5:#7A9A4E; --c6:#8C7BB8; --c7:#4FA3A5; --c8:#D1B15E;
}
.screen { width:448px; height:938px; background:var(--bg); display:flex; flex-direction:column; overflow:hidden; position:relative; }
.status { height:38px; flex:0 0 38px; display:flex; align-items:center; justify-content:space-between; padding:0 22px; font-size:12px; font-weight:700; }
.status-icons { display:flex; align-items:center; gap:5px; }
.content { flex:1; min-height:0; display:flex; flex-direction:column; gap:12px; padding:0 20px; overflow:hidden; }
.pagehead { min-height:52px; display:flex; align-items:flex-start; justify-content:space-between; gap:12px; }
.pagehead h1 { margin:0; font-size:26px; line-height:1.05; letter-spacing:-.5px; font-weight:700; }
.pagehead .sub { margin-top:4px; font-size:11px; color:var(--muted); }
.hdr { min-height:52px; display:flex; align-items:flex-start; gap:11px; }
.hdr .back { width:36px; height:36px; flex:0 0 36px; border-radius:12px; background:var(--surface); border:1px solid var(--outline); display:grid; place-items:center; color:var(--ink); }
.hdr .tt { font-size:22px; line-height:1.12; font-weight:700; letter-spacing:-.3px; }
.hdr .ss { font-size:10.5px; color:var(--muted); margin-top:3px; }
.hdr .right { margin-left:auto; }
.avatar { width:38px; height:38px; border-radius:50%; background:var(--primary); color:var(--on-primary); display:grid; place-items:center; font-size:15px; font-weight:700; }
.steps { display:flex; gap:5px; margin:2px 0 0; }
.steps span { flex:1; height:4px; border-radius:99px; background:var(--outline); }
.steps span.on { background:var(--primary); }
.steplabel { font-size:10px; font-weight:700; letter-spacing:.4px; color:var(--muted); text-transform:uppercase; }
.pill { display:inline-flex; align-items:center; gap:5px; padding:5px 9px; border-radius:999px; font-size:9.5px; font-weight:700; background:var(--surface-alt); color:var(--muted); white-space:nowrap; }
.pill.primary { background:color-mix(in srgb, var(--primary) 13%, transparent); color:var(--primary); }
.pill.accent { background:color-mix(in srgb, var(--accent) 15%, transparent); color:var(--accent); }
.pill.danger { background:color-mix(in srgb, var(--danger) 12%, transparent); color:var(--danger); }
.pill.success { background:color-mix(in srgb, var(--success) 13%, transparent); color:var(--success); }
.chips { display:flex; gap:7px; align-items:center; flex-wrap:wrap; }
.chip { display:inline-flex; align-items:center; gap:5px; padding:6px 10px; border-radius:999px; border:1px solid var(--outline); background:var(--surface); color:var(--muted); font-size:10px; font-weight:700; white-space:nowrap; }
.chip.active { background:var(--primary); border-color:var(--primary); color:var(--on-primary); }
.chip.accent { border-color:color-mix(in srgb, var(--accent) 40%, var(--outline)); color:var(--accent); background:color-mix(in srgb, var(--accent) 9%, var(--surface)); }
.chip .dot { width:5px; height:5px; border-radius:50%; background:currentColor; opacity:.75; }
.card { background:var(--surface); border:1px solid var(--outline); border-radius:16px; }
.pad { padding:14px; }
.section { font-size:13px; font-weight:700; }
.section-row { display:flex; align-items:baseline; justify-content:space-between; }
.section-row .meta { font-size:10px; color:var(--muted); }
.btn { height:48px; border-radius:14px; background:var(--primary); color:var(--on-primary); font-size:13.5px; font-weight:700; display:flex; align-items:center; justify-content:center; gap:8px; }
.btn.ghost { background:var(--surface); color:var(--ink); border:1px solid var(--outline); }
.btn.small { height:38px; border-radius:11px; font-size:11.5px; padding:0 14px; }
.btn.danger { background:color-mix(in srgb, var(--danger) 11%, var(--surface)); color:var(--danger); border:1px solid color-mix(in srgb, var(--danger) 25%, var(--outline)); }
.textbtn { color:var(--primary); font-size:11.5px; font-weight:700; }
.footer { padding:12px 20px 20px; background:var(--bg); display:flex; flex-direction:column; gap:9px; flex:0 0 auto; }
.footer .note { text-align:center; font-size:9.5px; color:var(--muted); line-height:1.4; }
.spacer { flex:1; min-height:0; }
.nav { height:72px; flex:0 0 72px; background:var(--surface); border-top:1px solid var(--outline); display:flex; align-items:flex-start; justify-content:space-around; padding:9px 10px 0; position:relative; }
.nav .item { width:64px; display:flex; flex-direction:column; align-items:center; gap:3px; color:var(--muted); font-size:9px; font-weight:700; }
.nav .item.active { color:var(--primary); }
.nav .item .ico { width:24px; height:24px; display:grid; place-items:center; border-radius:8px; }
.nav .item.active .ico { background:color-mix(in srgb, var(--primary) 13%, transparent); }
.homebar { position:absolute; bottom:6px; left:50%; transform:translateX(-50%); width:120px; height:4px; border-radius:99px; background:var(--ink); opacity:.5; }
.input { height:44px; border-radius:13px; background:var(--surface); border:1px solid var(--outline); display:flex; align-items:center; gap:8px; padding:0 12px; color:var(--muted); font-size:11.5px; }
.input .ph { color:var(--muted); }
.searchicon { color:var(--muted); }
.list { background:var(--surface); border:1px solid var(--outline); border-radius:16px; overflow:hidden; }
.row { display:flex; align-items:center; gap:10px; padding:10px 12px; min-height:54px; }
.row + .row { border-top:1px solid var(--outline); }
.row .grow { flex:1; min-width:0; }
.row .name { font-size:12px; font-weight:700; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.row .desc { font-size:9.5px; color:var(--muted); margin-top:2px; display:flex; align-items:center; gap:4px; flex-wrap:wrap; }
.row .amt { font-size:12px; font-weight:700; white-space:nowrap; text-align:right; }
.row .amt small { display:block; font-size:8.5px; color:var(--muted); font-weight:500; margin-top:1px; }
.avatar-sm { width:32px; height:32px; flex:0 0 32px; border-radius:10px; display:grid; place-items:center; font-size:12px; font-weight:700; }
.checkbox { width:22px; height:22px; flex:0 0 22px; border-radius:7px; border:1.5px solid var(--outline); background:var(--surface); display:grid; place-items:center; }
.checkbox.on { background:var(--primary); border-color:var(--primary); color:var(--on-primary); }
.toggle { width:42px; height:24px; border-radius:99px; background:var(--outline); position:relative; flex:0 0 42px; }
.toggle.on { background:var(--primary); }
.toggle::after { content:""; position:absolute; top:3px; left:3px; width:18px; height:18px; border-radius:50%; background:#fff; }
.toggle.on::after { left:21px; }
.segmented { display:flex; background:var(--surface-alt); border-radius:12px; padding:3px; gap:3px; }
.segmented .seg { flex:1; height:31px; border-radius:9px; display:grid; place-items:center; font-size:10px; font-weight:700; color:var(--muted); }
.segmented .seg.on { background:var(--surface); color:var(--ink); box-shadow:0 1px 2px rgba(20,32,28,.10); }
.progress { height:8px; border-radius:99px; background:var(--surface-alt); overflow:hidden; }
.progress span { display:block; height:100%; border-radius:99px; background:var(--primary); }
.statgrid { display:grid; grid-template-columns:1fr 1fr; gap:8px; }
.stat { background:var(--surface); border:1px solid var(--outline); border-radius:14px; padding:11px 12px; }
.stat .k { font-size:9px; color:var(--muted); font-weight:700; text-transform:uppercase; letter-spacing:.4px; }
.stat .v { font-size:20px; font-weight:700; letter-spacing:-.4px; margin-top:3px; }
.stat .s { font-size:9px; color:var(--muted); margin-top:1px; }
.divider { height:1px; background:var(--outline); }
.sheetwrap { position:absolute; left:0; right:0; top:38px; bottom:0; background:var(--bg); }
.sheetwrap .content { padding-top:4px; }
.backdrop { position:absolute; inset:0; background:rgba(20,32,28,.38); }
.sheet { position:absolute; left:0; right:0; bottom:0; background:var(--surface); border-radius:24px 24px 0 0; box-shadow:0 -16px 40px rgba(20,32,28,.18); padding:0 20px 20px; display:flex; flex-direction:column; overflow:hidden; }
.grabber { width:38px; height:4px; border-radius:99px; background:var(--outline); margin:8px auto 6px; flex:0 0 4px; }
.sheethead { display:flex; align-items:flex-start; justify-content:space-between; gap:10px; padding:2px 0 10px; }
.sheethead h2 { margin:0; font-size:19px; line-height:1.1; letter-spacing:-.3px; }
.sheethead .sub { font-size:10px; color:var(--muted); margin-top:3px; }
.close { width:30px; height:30px; border-radius:10px; background:var(--surface-alt); display:grid; place-items:center; color:var(--muted); }
.kv { display:grid; grid-template-columns:1fr 1fr; gap:9px 12px; }
.kv .k { font-size:9px; color:var(--muted); text-transform:uppercase; letter-spacing:.4px; font-weight:700; }
.kv .v { font-size:11px; font-weight:700; margin-top:2px; }
.why { display:flex; flex-direction:column; gap:7px; }
.why .item { display:flex; align-items:flex-start; gap:7px; font-size:9.5px; color:var(--muted); line-height:1.35; }
.why .tick { width:15px; height:15px; flex:0 0 15px; border-radius:50%; background:color-mix(in srgb, var(--success) 15%, transparent); color:var(--success); display:grid; place-items:center; font-size:9px; font-weight:700; }
.smsbox { background:var(--surface-alt); border-radius:12px; padding:10px 11px; font-size:9.5px; line-height:1.45; color:var(--ink); }
.smsmeta { display:flex; justify-content:space-between; font-size:8.5px; color:var(--muted); margin-bottom:5px; }
.trend { width:100%; height:118px; }
.legend { display:flex; flex-direction:column; gap:7px; }
.legend .lr { display:flex; align-items:center; gap:7px; font-size:10.5px; }
.legend .dot { width:8px; height:8px; border-radius:3px; flex:0 0 8px; }
.legend .pct { margin-left:auto; color:var(--muted); font-weight:700; }
.list.compact .row { min-height:46px; padding:7px 12px; }
.list.compact .avatar-sm { width:28px; height:28px; flex:0 0 28px; border-radius:9px; }
.donut { width:118px; height:118px; border-radius:50%; flex:0 0 118px; position:relative; background:conic-gradient(var(--c1) 0 32%, var(--c2) 32% 56%, var(--c3) 56% 74%, var(--c4) 74% 100%); }
.donut.post { background:conic-gradient(var(--c1) 0 32%, var(--c2) 32% 56%, var(--c3) 56% 82%, var(--c4) 82% 100%); }
.donut::after { content:""; position:absolute; inset:24px; border-radius:50%; background:var(--surface); }
.donut .center { position:absolute; inset:0; display:grid; place-content:center; text-align:center; z-index:1; }
.donut .center b { font-size:20px; line-height:1; letter-spacing:-.5px; }
.donut .center span { font-size:9px; color:var(--muted); margin-top:3px; letter-spacing:.5px; }
.hero { height:246px; border-radius:28px; background:var(--surface); border:1px solid var(--outline); display:grid; place-items:center; position:relative; overflow:hidden; }
.hero::before { content:""; position:absolute; width:210px; height:210px; border-radius:50%; background:color-mix(in srgb, var(--primary) 8%, transparent); }
.hero::after { content:""; position:absolute; width:150px; height:150px; border-radius:50%; background:color-mix(in srgb, var(--accent) 8%, transparent); }
.hero svg { position:relative; z-index:1; }
.brand { display:flex; align-items:center; gap:9px; }
.brand .mark { width:34px; height:34px; border-radius:12px; background:var(--primary); color:var(--on-primary); display:grid; place-items:center; font-size:15px; font-weight:700; }
.brand .nm { font-size:12px; font-weight:700; letter-spacing:.1px; }
.feature { display:flex; align-items:flex-start; gap:10px; }
.feature .fi { width:30px; height:30px; flex:0 0 30px; border-radius:10px; background:color-mix(in srgb, var(--primary) 10%, var(--surface)); color:var(--primary); display:grid; place-items:center; }
.feature .ft { font-size:11px; font-weight:700; }
.feature .fs { font-size:9.5px; color:var(--muted); margin-top:2px; line-height:1.35; }
.notice { display:flex; gap:8px; align-items:flex-start; padding:10px 11px; border-radius:12px; background:color-mix(in srgb, var(--accent) 10%, var(--surface)); border:1px solid color-mix(in srgb, var(--accent) 26%, var(--outline)); }
.notice .ni { width:22px; height:22px; flex:0 0 22px; border-radius:8px; background:var(--accent); color:#fff; display:grid; place-items:center; }
.notice .nt { font-size:10.5px; font-weight:700; }
.notice .ns { font-size:9px; color:var(--muted); margin-top:1px; line-height:1.35; }
.field-label { font-size:9px; font-weight:700; color:var(--muted); text-transform:uppercase; letter-spacing:.5px; margin-bottom:6px; }
.catgrid { display:grid; grid-template-columns:repeat(3,1fr); gap:7px; }
.cat { height:34px; border-radius:10px; border:1px solid var(--outline); background:var(--surface); color:var(--muted); display:grid; place-items:center; font-size:9.5px; font-weight:700; }
.cat.on { background:var(--primary); border-color:var(--primary); color:var(--on-primary); }
.setting-group { background:var(--surface); border:1px solid var(--outline); border-radius:16px; overflow:hidden; }
.setting { display:flex; align-items:center; gap:10px; padding:11px 12px; min-height:50px; }
.setting + .setting { border-top:1px solid var(--outline); }
.setting .si { width:30px; height:30px; flex:0 0 30px; border-radius:10px; background:var(--surface-alt); display:grid; place-items:center; color:var(--ink); }
.setting .st { font-size:11.5px; font-weight:700; }
.setting .ss { font-size:9px; color:var(--muted); margin-top:2px; }
.setting .chev { margin-left:auto; color:var(--muted); }
.setting .righttext { margin-left:auto; font-size:9.5px; color:var(--muted); }
.grouphead { display:flex; align-items:center; justify-content:space-between; padding:2px 4px; }
.grouphead .gh { font-size:10px; font-weight:700; letter-spacing:.6px; text-transform:uppercase; color:var(--muted); }
.grouphead .gc { font-size:9px; color:var(--muted); }
'''

ICONS = {
    "card": '<svg width="12" height="10" viewBox="0 0 12 10" fill="none"><rect x=".5" y="1" width="11" height="8" rx="1.6" stroke="currentColor" stroke-width="1.2"/><path d="M.5 3.4h11" stroke="currentColor" stroke-width="1.2"/></svg>',
    "bank": '<svg width="12" height="10" viewBox="0 0 12 10" fill="none"><path d="M1 3.5 6 1l5 2.5v1H1v-1Z" stroke="currentColor" stroke-width="1.1"/><path d="M2 5v3M5 5v3M8 5v3M10 5v3M1 8.5h10" stroke="currentColor" stroke-width="1.1" stroke-linecap="round"/></svg>',
    "search": '<svg width="15" height="15" viewBox="0 0 16 16" fill="none"><circle cx="7" cy="7" r="4.6" stroke="currentColor" stroke-width="1.5"/><path d="m10.4 10.4 3 3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>',
    "chev": '<svg width="7" height="12" viewBox="0 0 7 12" fill="none"><path d="m1 1 5 5-5 5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    "back": '<svg width="9" height="15" viewBox="0 0 9 15" fill="none"><path d="M8 1 1 7.5 8 14" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    "check": '<svg width="11" height="9" viewBox="0 0 12 10" fill="none"><path d="m1 5 3.2 3.2L11 1.2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    "close": '<svg width="11" height="11" viewBox="0 0 12 12" fill="none"><path d="m1 1 10 10M11 1 1 11" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>',
    "info": '<svg width="12" height="12" viewBox="0 0 14 14" fill="none"><circle cx="7" cy="7" r="5.6" stroke="currentColor" stroke-width="1.3"/><path d="M7 6.2v3.1" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/><circle cx="7" cy="4.2" r=".8" fill="currentColor"/></svg>',
    "warning": '<svg width="13" height="13" viewBox="0 0 14 14" fill="none"><path d="M7 1.5 13 12H1L7 1.5Z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/><path d="M7 5v3.2" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/><circle cx="7" cy="10.2" r=".85" fill="currentColor"/></svg>',
    "sms": '<svg width="16" height="16" viewBox="0 0 18 18" fill="none"><path d="M2.5 3.5h13v9h-7l-3.4 2.6V12.5h-2.6v-9Z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/><path d="M5.5 6.5h7M5.5 9h4.5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/></svg>',
    "shield": '<svg width="18" height="20" viewBox="0 0 20 22" fill="none"><path d="M10 1.5 18 4.5v6.2c0 4.8-3.4 8.1-8 9.8-4.6-1.7-8-5-8-9.8V4.5l8-3Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/><path d="m6.4 10.4 2.3 2.3 4.7-4.7" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    "lock": '<svg width="16" height="20" viewBox="0 0 18 22" fill="none"><rect x="2.5" y="9" width="13" height="11" rx="2.5" stroke="currentColor" stroke-width="1.5"/><path d="M5.5 9V6.5a3.5 3.5 0 0 1 7 0V9" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><circle cx="9" cy="14" r="1.2" fill="currentColor"/></svg>',
    "cloud": '<svg width="16" height="13" viewBox="0 0 18 14" fill="none"><path d="M5 12.5h8.2a3.3 3.3 0 0 0 .3-6.6A5 5 0 0 0 4 7.1a2.8 2.8 0 0 0 1 5.4Z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/><path d="M9 10V6.2M7.3 7.7 9 6l1.7 1.7" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    "csv": '<svg width="15" height="16" viewBox="0 0 16 18" fill="none"><path d="M3 1.5h7l3 3v12H3v-15Z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/><path d="M10 1.8V5h3" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/><path d="M5.2 9h5.6M5.2 11.5h5.6M5.2 14h3.5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/></svg>',
    "home": '<svg width="18" height="18" viewBox="0 0 20 20" fill="none"><path d="M3 8.6 10 3l7 5.6V17H3V8.6Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><path d="M8 17v-5h4v5" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/></svg>',
    "activity": '<svg width="18" height="18" viewBox="0 0 20 20" fill="none"><path d="M3 5h14M3 10h14M3 15h9" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>',
    "insights": '<svg width="18" height="18" viewBox="0 0 20 20" fill="none"><path d="M4 16V9M10 16V4M16 16v-4" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/></svg>',
    "settings": '<svg width="18" height="18" viewBox="0 0 20 20" fill="none"><circle cx="10" cy="10" r="2.6" stroke="currentColor" stroke-width="1.7"/><path d="M10 2.6v2M10 15.4v2M2.6 10h2M15.4 10h2M4.8 4.8l1.4 1.4M13.8 13.8l1.4 1.4M15.2 4.8l-1.4 1.4M6.2 13.8l-1.4 1.4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>',
}


def statusbar():
    return f'''<div class="status"><span>9:41</span><div class="status-icons">
      <svg width="15" height="11" viewBox="0 0 15 11" fill="none"><rect x="0" y="7" width="3" height="4" rx="1" fill="currentColor"/><rect x="4" y="5" width="3" height="6" rx="1" fill="currentColor"/><rect x="8" y="2.5" width="3" height="8.5" rx="1" fill="currentColor"/><rect x="12" y="0" width="3" height="11" rx="1" fill="currentColor"/></svg>
      <svg width="15" height="11" viewBox="0 0 15 11" fill="none"><path d="M1 4.2C4.8.9 10.2.9 14 4.2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/><path d="M3.4 6.8c2.5-2.1 5.7-2.1 8.2 0" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/><circle cx="7.5" cy="9.5" r="1.2" fill="currentColor"/></svg>
      <svg width="20" height="10" viewBox="0 0 20 10" fill="none"><rect x=".5" y=".5" width="16" height="9" rx="2.5" stroke="currentColor"/><rect x="2" y="2" width="12" height="6" rx="1.5" fill="currentColor"/><path d="M18 3.3v3.4c1.2-.3 1.6-.9 1.6-1.7S19.2 3.6 18 3.3Z" fill="currentColor"/></svg>
    </div></div>'''


def bottom_nav(active):
    items = [("home", "Home"), ("activity", "Activity"), ("insights", "Insights"), ("settings", "Settings")]
    html = '<div class="nav">'
    for key, label in items:
        cls = "item active" if key == active else "item"
        html += f'<div class="{cls}"><div class="ico">{ICONS[key]}</div>{label}</div>'
    html += '<div class="homebar"></div></div>'
    return html


def pagehead(title, sub=None, right=""):
    s = f'<div class="sub">{sub}</div>' if sub else ""
    return f'<div class="pagehead"><div><h1>{title}</h1>{s}</div>{right}</div>'


def hdr(title, sub=None, back=True, right=""):
    b = f'<div class="back">{ICONS["back"]}</div>' if back else ""
    s = f'<div class="ss">{sub}</div>' if sub else ""
    return f'<div class="hdr">{b}<div><div class="tt">{title}</div>{s}</div>{right}</div>'


def steps(n, total=4):
    return '<div class="steps">' + "".join(f'<span class="{"on" if i < n else ""}"></span>' for i in range(total)) + '</div>'


def card(inner, cls="card"):
    return f'<div class="{cls}">{inner}</div>'


def button(label, cls="btn", icon=""):
    return f'<div class="{cls}">{icon}{label}</div>'


def txn_row(merchant, category, instrument, amount, when, color, initial, channel="card", flag=False):
    ic = ICONS["card"] if channel == "card" else ICONS["bank"]
    flag_html = ' <span class="pill accent" style="padding:2px 5px;font-size:8px">Review</span>' if flag else ""
    return f'''<div class="row">
      <div class="avatar-sm" style="background:color-mix(in srgb, {color} 16%, var(--surface));color:{color}">{initial}</div>
      <div class="grow"><div class="name">{merchant}</div><div class="desc">{category} · <span style="display:inline-flex;align-items:center;gap:3px">{ic}{instrument}</span>{flag_html}</div></div>
      <div class="amt">−{amount}<small>{when}</small></div>
    </div>'''


def make(name, body, screen_class="screen"):
    html = f'''<!doctype html><html lang="en"><head><meta charset="utf-8"><style>{CSS}</style></head><body><div class="{screen_class}">{statusbar()}{body}</div></body></html>'''
    (OUT / f"{name}.html").write_text(html, encoding="utf-8")


# 01 Welcome & privacy
hero_svg = '''<svg width="150" height="150" viewBox="0 0 150 150" fill="none">
  <rect x="42" y="22" width="66" height="108" rx="16" fill="#FFFFFF" stroke="#0E6B5C" stroke-width="3"/>
  <rect x="55" y="40" width="40" height="8" rx="4" fill="#0E6B5C" opacity=".18"/>
  <rect x="55" y="55" width="30" height="6" rx="3" fill="#0E6B5C" opacity=".12"/>
  <rect x="55" y="70" width="36" height="22" rx="7" fill="#0E6B5C" opacity=".10"/>
  <circle cx="105" cy="104" r="22" fill="#C97B2E"/>
  <path d="M96 104h18M105 95v18" stroke="white" stroke-width="3" stroke-linecap="round" opacity=".9"/>
</svg>'''
body = f'''
<div class="content" style="gap:14px;padding-top:8px">
  <div class="brand"><div class="mark">S</div><div class="nm">SMSCostTracker</div><div style="margin-left:auto" class="pill">v0.2</div></div>
  <div class="hero">{hero_svg}</div>
  <div><div style="font-size:29px;line-height:1.12;font-weight:700;letter-spacing:-.7px">Your spending,<br>from your SMS.</div>
  <div style="font-size:11.5px;line-height:1.55;color:var(--muted);margin-top:10px">Turn bank messages into clear transactions and charts. Parsing happens entirely on this phone.</div></div>
  <div style="display:flex;flex-direction:column;gap:11px;margin-top:2px">
    <div class="feature"><div class="fi">{ICONS["shield"]}</div><div><div class="ft">On-device only</div><div class="fs">No server, no account, no SMS upload.</div></div></div>
    <div class="feature"><div class="fi">{ICONS["bank"]}</div><div><div class="ft">No bank login</div><div class="fs">We read only the bank threads you pick.</div></div></div>
    <div class="feature"><div class="fi">{ICONS["lock"]}</div><div><div class="ft">You stay in control</div><div class="fs">Review every card and account before import.</div></div></div>
  </div>
</div>
<div class="footer">
  {button("Get started")}
  <div class="note">By continuing you agree to read only the threads you select. You can revoke SMS access in Android Settings at any time.</div>
</div>'''
make("01-welcome", body)

# 02 SMS permission
body = f'''
<div class="content">
  {hdr("Allow SMS access", "Step 1 of 4 · Permission", right='<div class="pill primary">Required</div>')}
  {steps(1)}
  <div class="hero" style="height:210px">
    <svg width="120" height="120" viewBox="0 0 120 120" fill="none">
      <rect x="14" y="24" width="72" height="56" rx="14" fill="#F1ECE2" stroke="#0E6B5C" stroke-width="2.4"/>
      <path d="M30 44h40M30 54h28M30 64h34" stroke="#0E6B5C" stroke-width="3" stroke-linecap="round" opacity=".65"/>
      <path d="M26 80l-8 12 20-6" fill="#F1ECE2" stroke="#0E6B5C" stroke-width="2.4" stroke-linejoin="round"/>
      <circle cx="88" cy="76" r="22" fill="#0E6B5C"/>
      <path d="M78 76l7 7 13-14" stroke="white" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round"/>
    </svg>
  </div>
  <div><div style="font-size:22px;font-weight:700;line-height:1.15;letter-spacing:-.4px">We need to read bank messages to build your transactions.</div>
  <div style="font-size:11px;line-height:1.5;color:var(--muted);margin-top:8px">Android will show a system prompt. SMSCostTracker only reads the conversations you select in the next step.</div></div>
  <div class="card pad" style="display:flex;flex-direction:column;gap:11px">
    <div class="feature"><div class="fi">{ICONS["sms"]}</div><div><div class="ft">Read SMS</div><div class="fs">Needed to find purchase, transfer and bill messages.</div></div></div>
    <div class="feature"><div class="fi">{ICONS["shield"]}</div><div><div class="ft">No internet required</div><div class="fs">Parsing and classification run locally.</div></div></div>
    <div class="feature"><div class="fi">{ICONS["settings"]}</div><div><div class="ft">Revoke anytime</div><div class="fs">Android Settings → Apps → Permissions.</div></div></div>
  </div>
</div>
<div class="footer">
  {button("Allow SMS access")}
  <div class="btn ghost" style="height:40px;font-size:12px">Not now</div>
  <div class="note">We never send SMS or share message text.</div>
</div>'''
make("02-permission", body)

# 03 Choose threads
threads = [
    ("SAB", "1,214 messages", "Last 21 Oct", True, "SA"),
    ("Al Rajhi", "486 messages", "Last 20 Oct", True, "AR"),
    ("CIB", "212 messages", "Last 18 Oct", True, "CI"),
    ("SNB", "164 messages", "Last 16 Oct", True, "SN"),
    ("Riyad Bank", "98 messages", "Last 14 Oct", True, "RB"),
    ("ANB", "41 messages", "Last 02 Oct", False, "AN"),
    ("STC Bank", "87 messages", "Last 21 Oct", False, "ST"),
]
rows = ""
for nm, count, last, on, ini in threads:
    rows += f'''<div class="row">
      <div class="checkbox {'on' if on else ''}">{ICONS['check'] if on else ''}</div>
      <div class="avatar-sm" style="background:var(--surface-alt);color:var(--primary)">{ini}</div>
      <div class="grow"><div class="name">{nm}</div><div class="desc">{count} · {last}</div></div>
    </div>'''
body = f'''
<div class="content">
  {hdr("Choose bank threads", "Step 2 of 4 · Select conversations")}
  {steps(2)}
  <div class="input">{ICONS['search']}<span class="ph">Search sender or number</span></div>
  <div class="section-row"><div class="section">Suggested bank threads</div><div class="textbtn">Select all</div></div>
  <div class="list">{rows}</div>
  <div class="notice" style="margin-top:auto"><div class="ni">{ICONS['info']}</div><div><div class="nt">Only selected threads are scanned</div><div class="ns">You can add or remove threads later in Settings → Banks & threads.</div></div></div>
</div>
<div class="footer">
  <div style="display:flex;justify-content:space-between;align-items:center"><div style="font-size:11px;font-weight:700">5 selected</div><div style="font-size:10px;color:var(--muted)">2,174 messages</div></div>
  {button("Scan selected")}
</div>'''
make("03-threads", body)

# 04 Scan progress
body = f'''
<div class="content">
  {hdr("Scanning", "Step 3 of 4 · On-device", right='<div class="pill primary">Local only</div>')}
  {steps(3)}
  <div><div style="font-size:22px;font-weight:700;line-height:1.15;letter-spacing:-.4px">Reading your bank messages</div>
  <div style="font-size:11px;color:var(--muted);margin-top:5px">SAB · 842 of 1,214 messages</div></div>
  <div class="card pad">
    <div style="display:flex;justify-content:space-between;font-size:10px;font-weight:700;margin-bottom:7px"><span>Overall progress</span><span style="color:var(--primary)">69%</span></div>
    <div class="progress"><span style="width:69%"></span></div>
    <div class="statgrid" style="margin-top:12px">
      <div class="stat"><div class="k">Transactions</div><div class="v">612</div><div class="s">from 2,174 messages</div></div>
      <div class="stat"><div class="k">Skipped</div><div class="v">148</div><div class="s">balances, OTPs, duplicates</div></div>
      <div class="stat"><div class="k">Duplicates</div><div class="v">22</div><div class="s">collapsed safely</div></div>
      <div class="stat"><div class="k">Unparsed</div><div class="v">6</div><div class="s">you can review later</div></div>
    </div>
  </div>
  <div class="section">Currently parsing</div>
  <div class="card pad">
    <div style="display:flex;align-items:center;gap:8px"><div class="avatar-sm" style="background:color-mix(in srgb, var(--c1) 15%, var(--surface));color:var(--c1)">TA</div><div><div style="font-size:11px;font-weight:700">Tamimi Markets</div><div style="font-size:9px;color:var(--muted)">SAB · 21 Oct, 14:32</div></div><div class="pill success" style="margin-left:auto">Groceries</div></div>
    <div class="smsbox" style="margin-top:10px"><div class="smsmeta"><span>Purchase</span><span>SAR 236.50</span></div>Purchase of SAR 236.50 at TAMIMI MARKETS using card ending 1214...</div>
  </div>
  <div class="notice" style="margin-top:auto"><div class="ni">{ICONS['shield']}</div><div><div class="nt">Your messages stay on this phone</div><div class="ns">Keep this screen open while the scan finishes. Nothing is uploaded.</div></div></div>
</div>
<div class="footer"><div class="btn ghost" style="height:40px;font-size:12px">Cancel scan</div></div>'''
make("04-scan", body)

# 05 Review instruments step 1
groups = [
    ("SAB", "4 values", "3 cards · 1 account", "SA", "Start here", "primary"),
    ("Al Rajhi", "2 values", "1 card · 1 account", "AR", "Review", ""),
    ("CIB", "2 values", "1 card · 1 account", "CI", "Review", ""),
    ("SNB", "2 values", "1 card · 1 account", "SN", "Review", ""),
    ("Riyad Bank", "2 values", "1 card · 1 account", "RB", "Review", ""),
]
grows = ""
for nm, cnt, split, ini, action, cls in groups:
    grows += f'''<div class="setting">
      <div class="si" style="background:var(--surface-alt);color:var(--primary);font-size:10px;font-weight:700">{ini}</div>
      <div><div class="st">{nm}</div><div class="ss">{cnt} · {split}</div></div>
      <div class="pill {cls}" style="margin-left:auto">{action}</div>
    </div>'''
body = f'''
<div class="content">
  {hdr("Review instruments", "12 values found in 5 threads", right='<div class="pill">1 of 2</div>')}
  <div><div style="font-size:21px;font-weight:700;line-height:1.15;letter-spacing:-.4px">Which numbers are cards,<br>and which are accounts?</div>
  <div style="font-size:10.5px;color:var(--muted);line-height:1.45;margin-top:6px">We grouped the numbers by sender and suggested a type from the SMS wording. You can change any of them later.</div></div>
  <div class="setting-group">{grows}</div>
  <div class="notice"><div class="ni">{ICONS['warning']}</div><div><div class="nt">12 values need review</div><div class="ns">Transactions still import. Unverified values are tagged until you confirm them.</div></div></div>
</div>
<div class="footer">
  {button("Review SAB first")}
  <div class="btn ghost" style="height:40px;font-size:12px">Review later</div>
  <div class="note">Only unverified values are shown. You can pause and return from Home.</div>
</div>'''
make("05-review-groups", body)

# 06 Review instruments step 2
inst = [
    ("1214", "318 txns", "Card", "SMS says “card ending 1214”", True),
    ("001", "296 txns", "Account", "SMS says “from account 001”", False),
    ("0542", "74 txns", "Card", "Seen in card purchase messages", True),
    ("9981", "12 txns", "Card", "Seen in online card messages", True),
]
irows = ""
for digits, txns, typ, hint, is_card in inst:
    card_on = "on" if is_card else ""
    acct_on = "" if is_card else "on"
    irows += f'''<div style="padding:11px 12px;border-top:1px solid var(--outline)">
      <div style="display:flex;align-items:center;gap:9px"><div style="font-size:14px;font-weight:700;letter-spacing:.2px">{digits}</div><div class="pill">{txns}</div><div class="pill {("primary" if is_card else "")}" style="margin-left:auto">{('Card' if is_card else 'Account')} suggested</div></div>
      <div style="font-size:9px;color:var(--muted);margin-top:4px">{hint}</div>
      <div class="segmented" style="margin-top:8px"><div class="seg {card_on}">Card</div><div class="seg {acct_on}">Account</div></div>
    </div>'''
body = f'''
<div class="content">
  {hdr("Confirm SAB numbers", "4 values · suggested from SMS wording", right='<div class="pill">2 of 2</div>')}
  <div class="card" style="overflow:hidden">{irows}</div>
  <div class="textbtn" style="text-align:center">Mark all SAB values as cards</div>
  <div class="notice" style="margin-top:auto"><div class="ni">{ICONS['info']}</div><div><div class="nt">Why we suggest a type</div><div class="ns">“card ending 1214” means Card. “from account 001” means Account. You can override every value.</div></div></div>
</div>
<div class="footer">
  {button("Save 4 and review Al Rajhi")}
  <div class="btn ghost" style="height:40px;font-size:12px">Save and review later</div>
</div>'''
make("06-review-confirm", body)

# 07 Import complete
body = f'''
<div class="content" style="gap:14px">
  <div class="pagehead"><div><h1>Import complete</h1><div class="sub">First run · finished</div></div><div class="pill success">{ICONS['check']} Ready</div></div>
  <div class="hero" style="height:196px">
    <svg width="118" height="118" viewBox="0 0 118 118" fill="none"><circle cx="59" cy="59" r="50" fill="#0E6B5C" opacity=".1"/><circle cx="59" cy="59" r="38" fill="#0E6B5C"/><path d="m42 59 11 11 23-25" stroke="white" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/></svg>
  </div>
  <div style="text-align:center"><div style="font-size:24px;font-weight:700;letter-spacing:-.5px">You're all set.</div><div style="font-size:11px;line-height:1.5;color:var(--muted);margin-top:6px">1,284 transactions from 2,174 messages are ready on this phone.</div></div>
  <div class="statgrid">
    <div class="stat"><div class="k">Transactions</div><div class="v">1,284</div><div class="s">Jan – Oct 2026</div></div>
    <div class="stat"><div class="k">Skipped</div><div class="v">890</div><div class="s">balances, OTPs, duplicates</div></div>
    <div class="stat"><div class="k">Instruments</div><div class="v">6 / 12</div><div class="s">confirmed</div></div>
    <div class="stat"><div class="k">Unparsed</div><div class="v">6</div><div class="s">messages to review</div></div>
  </div>
  <div class="notice"><div class="ni">{ICONS['warning']}</div><div><div class="nt">6 card/account values need review</div><div class="ns">CIB, SNB and Riyad Bank can be reviewed now or later from Home.</div></div></div>
</div>
<div class="footer">
  {button("Go to Home")}
  <div class="btn ghost" style="height:40px;font-size:12px">Review 6 values</div>
</div>'''
make("07-complete", body)

# 08 Home
cat_legend_home = f'''<div class="legend">
  <div class="lr"><span class="dot" style="background:var(--c1)"></span><span>Groceries</span><span class="pct">32%</span></div>
  <div class="lr"><span class="dot" style="background:var(--c2)"></span><span>Dining</span><span class="pct">24%</span></div>
  <div class="lr"><span class="dot" style="background:var(--c3)"></span><span>Shopping</span><span class="pct">18%</span></div>
  <div class="lr"><span class="dot" style="background:var(--c4)"></span><span>Other</span><span class="pct">26%</span></div>
</div>'''
cat_legend_insights = f'''<div class="legend">
  <div class="lr"><span class="dot" style="background:var(--c1)"></span><span>Groceries</span><span class="pct">32%</span></div>
  <div class="lr"><span class="dot" style="background:var(--c2)"></span><span>Dining</span><span class="pct">24%</span></div>
  <div class="lr"><span class="dot" style="background:var(--c3)"></span><span>Shopping</span><span class="pct">26%</span></div>
  <div class="lr"><span class="dot" style="background:var(--c4)"></span><span>Other</span><span class="pct">18%</span></div>
</div>'''
body = f'''
<div class="content">
  {pagehead("Home", "October 2026", right='<div class="avatar">A</div>')}
  <div class="chips"><div class="chip active"><span class="dot"></span>SAR</div><div class="chip"><span class="dot"></span>EGP</div><div class="spacer"></div><div class="chip">All accounts</div></div>
  <div style="background:var(--primary);color:var(--on-primary);border-radius:22px;padding:15px 18px 13px;box-shadow:0 10px 24px rgba(0,0,0,.10)">
    <div style="font-size:9px;letter-spacing:1.2px;font-weight:700;opacity:.78;text-transform:uppercase">Spent in October</div>
    <div style="font-size:32px;font-weight:700;letter-spacing:-.7px;margin-top:2px">SAR 4,820.00</div>
    <div style="font-size:10px;opacity:.78;margin-top:2px">▲ 12% vs September · also EGP 3,240</div>
    <div style="display:flex;margin-top:12px;padding-top:10px;border-top:1px solid rgba(255,255,255,.22)">
      <div style="flex:1"><div style="font-size:9px;opacity:.72;display:flex;align-items:center;gap:4px">{ICONS['card']}Cards</div><div style="font-size:13px;font-weight:700;margin-top:2px">SAR 3,140</div></div>
      <div style="flex:1;border-left:1px solid rgba(255,255,255,.22);padding-left:14px"><div style="font-size:9px;opacity:.72;display:flex;align-items:center;gap:4px">{ICONS['bank']}Accounts</div><div style="font-size:13px;font-weight:700;margin-top:2px">SAR 1,680</div></div>
    </div>
  </div>
  <div class="notice"><div class="ni">{ICONS['warning']}</div><div class="grow"><div class="nt">6 card/account values need review</div><div class="ns">CIB, SNB and Riyad Bank</div></div>{ICONS['chev']}</div>
  <div class="card pad">
    <div class="section-row"><div class="section">Top categories</div><div class="meta">October</div></div>
    <div style="display:flex;align-items:center;gap:14px;margin-top:8px">
      <div class="donut" style="width:104px;height:104px;flex:0 0 104px"><div class="center"><b>4.8k</b><span>SAR</span></div></div>
      {cat_legend_home}
    </div>
  </div>
  <div class="section-row"><div class="section">Recent</div><div class="textbtn">See all</div></div>
  <div class="list">
    {txn_row("Amazon Now", "Shopping", "Card 1214", "SAR 189.00", "Today", "var(--c1)", "A")}
    {txn_row("Tamimi Markets", "Groceries", "Account 001", "SAR 236.50", "Yesterday", "var(--c2)", "T", "bank")}
    {txn_row("Careem", "Transport", "Card 1214", "SAR 38.00", "Oct 19", "var(--c3)", "C")}
  </div>
</div>
{bottom_nav("home")}'''
make("08-home", body)

# 09 Activity
body = f'''
<div class="content">
  {pagehead("Activity", "128 transactions · October")}
  <div class="input">{ICONS['search']}<span class="ph">Search merchant or amount</span></div>
  <div class="chips"><div class="chip active">This month {ICONS['chev']}</div><div class="chip accent">4 filters</div><div class="chip">SAR {ICONS['chev']}</div><div class="textbtn" style="margin-left:auto">Clear</div></div>
  <div class="grouphead"><div class="gh">Today · 21 Oct</div><div class="gc">−SAR 463.50</div></div>
  <div class="list">
    {txn_row("Amazon Now", "Shopping", "Card 1214", "SAR 189.00", "14:32", "var(--c1)", "A")}
    {txn_row("Tamimi Markets", "Groceries", "Account 001", "SAR 236.50", "12:08", "var(--c2)", "T", "bank")}
    {txn_row("Careem", "Transport", "Card 1214", "SAR 38.00", "09:15", "var(--c3)", "C")}
  </div>
  <div class="grouphead"><div class="gh">Yesterday · 20 Oct</div><div class="gc">−SAR 1,433.50</div></div>
  <div class="list">
    {txn_row("Jarir Bookstore", "Other", "Card 4432", "SAR 1,249.00", "19:42", "var(--c4)", "J", "card", True)}
    {txn_row("STC", "Telecom", "Account 001", "SAR 120.00", "11:00", "var(--c1)", "S", "bank")}
    {txn_row("HungerStation", "Dining", "Card 1214", "SAR 64.50", "20:21", "var(--c2)", "H")}
  </div>
  <div class="grouphead"><div class="gh">18 Oct</div><div class="gc">−SAR 407.00</div></div>
  <div class="list">
    {txn_row("Saudi Electricity", "Utilities", "Account 001", "SAR 312.00", "08:05", "var(--c3)", "S", "bank")}
    {txn_row("Petromin", "Fuel", "Card 1214", "SAR 95.00", "07:48", "var(--c5)", "P")}
  </div>
</div>
{bottom_nav("activity")}'''
make("09-activity", body)

# 10 Filters sheet
activity_bg = f'''<div class="content">
  {pagehead("Activity", "128 transactions · October")}
  <div class="input">{ICONS['search']}<span class="ph">Search merchant or amount</span></div>
  <div class="chips"><div class="chip active">This month</div><div class="chip accent">4 filters</div><div class="chip">SAR</div></div>
  <div class="grouphead"><div class="gh">Today · 21 Oct</div><div class="gc">−SAR 463.50</div></div>
  <div class="list">{txn_row("Amazon Now", "Shopping", "Card 1214", "SAR 189.00", "14:32", "var(--c1)", "A")}</div>
</div>'''
filters_sheet = f'''<div class="sheet" style="height:748px">
  <div class="grabber"></div>
  <div class="sheethead"><div><h2>Filters</h2><div class="sub">4 active · 18 matching transactions</div></div><div class="textbtn">Clear all</div></div>
  <div class="field-label">Period</div>
  <div class="chips"><div class="chip active">This month</div><div class="chip">Last month</div><div class="chip">Quarter</div><div class="chip">Custom</div></div>
  <div class="field-label" style="margin-top:12px">Category</div>
  <div class="chips"><div class="chip">All</div><div class="chip">Groceries</div><div class="chip">Dining</div><div class="chip active">Shopping</div><div class="chip">Transport</div><div class="chip">More</div></div>
  <div class="field-label" style="margin-top:12px">Account</div>
  <div class="chips"><div class="chip">All accounts</div><div class="chip active">SAB 1214</div><div class="chip">SAB 001</div><div class="chip">Al Rajhi 4432</div><div class="chip">More</div></div>
  <div class="field-label" style="margin-top:12px">Currency</div>
  <div class="chips"><div class="chip active">SAR</div><div class="chip">EGP</div><div class="chip">All</div></div>
  <div class="field-label" style="margin-top:12px">Merchant</div>
  <div class="input" style="justify-content:space-between"><span style="color:var(--ink);font-weight:700">Amazon</span><span class="close" style="width:24px;height:24px;background:var(--surface-alt)">{ICONS['close']}</span></div>
  <div class="spacer"></div>
  {button("Show 18 transactions")}
</div>'''
body = f'<div class="sheetwrap">{activity_bg}<div class="backdrop"></div>{filters_sheet}</div>'
make("10-filters", body)

# 11 Transaction detail sheet
activity_bg2 = f'''<div class="content">
  {pagehead("Activity", "128 transactions · October")}
  <div class="input">{ICONS['search']}<span class="ph">Search merchant or amount</span></div>
  <div class="chips"><div class="chip active">This month</div><div class="chip accent">4 filters</div><div class="chip">SAR</div></div>
  <div class="list">{txn_row("Amazon Now", "Shopping", "Card 1214", "SAR 189.00", "14:32", "var(--c1)", "A")}</div>
</div>'''
detail_sheet = f'''<div class="sheet" style="height:804px">
  <div class="grabber"></div>
  <div class="sheethead"><div><h2>Transaction</h2><div class="sub">Parsed from one SMS</div></div><div class="close">{ICONS['close']}</div></div>
  <div style="display:flex;align-items:center;gap:11px">
    <div class="avatar-sm" style="width:42px;height:42px;flex:0 0 42px;border-radius:13px;background:color-mix(in srgb, var(--c4) 16%, var(--surface));color:var(--c4);font-size:15px">J</div>
    <div><div style="font-size:16px;font-weight:700">Jarir Bookstore</div><div style="font-size:10px;color:var(--muted);margin-top:2px">20 Oct 2026 · 19:42</div></div>
    <div style="margin-left:auto;text-align:right"><div style="font-size:18px;font-weight:700">−SAR 1,249.00</div><div class="pill accent" style="margin-top:3px">Other · 62% match</div></div>
  </div>
  <div class="card pad" style="margin-top:11px">
    <div class="section" style="margin-bottom:9px">What we parsed</div>
    <div class="kv">
      <div><div class="k">Amount</div><div class="v">SAR 1,249.00</div></div>
      <div><div class="k">Merchant</div><div class="v">Jarir Bookstore</div></div>
      <div><div class="k">Category</div><div class="v">Other · 62% confidence</div></div>
      <div><div class="k">Instrument</div><div class="v">Al Rajhi 4432 · Card</div></div>
      <div><div class="k">Occurred</div><div class="v">20 Oct 2026 · 19:42</div></div>
      <div><div class="k">Source</div><div class="v">Al Rajhi SMS</div></div>
    </div>
  </div>
  <div class="card pad" style="margin-top:10px">
    <div class="section-row"><div class="section">Source SMS</div><div class="textbtn">Show full</div></div>
    <div class="smsbox" style="margin-top:8px"><div class="smsmeta"><span>Al Rajhi · 20 Oct 2026</span><span>19:42</span></div>Purchase of SAR 1,249.00 at JARIR BOOKSTORE using card ending 4432 on 20/10/26 19:42. Available balance SAR 8,214.30.</div>
  </div>
  <div class="card pad" style="margin-top:10px">
    <div class="section" style="margin-bottom:7px">Why this result</div>
    <div class="why">
      <div class="item"><span class="tick">{ICONS['check']}</span><span>Merchant matched <b style="color:var(--ink)">JARIR BOOKSTORE</b> in the SMS.</span></div>
      <div class="item"><span class="tick">{ICONS['check']}</span><span>Card channel detected from “card ending 4432”.</span></div>
      <div class="item"><span class="tick">{ICONS['check']}</span><span>Balance line ignored, so only the purchase amount is used.</span></div>
    </div>
  </div>
  <div class="spacer"></div>
  {button("Change category")}
  <div class="btn ghost" style="height:40px;font-size:11.5px;margin-top:8px">Change card/account</div>
  <div style="text-align:center;color:var(--danger);font-size:10.5px;font-weight:700;margin-top:10px">Not spending</div>
</div>'''
body = f'<div class="sheetwrap">{activity_bg2}<div class="backdrop"></div>{detail_sheet}</div>'
make("11-detail", body)

# 12 Category correction sheet
detail_bg = f'''<div class="content">
  {pagehead("Activity", "128 transactions · October")}
  <div class="input">{ICONS['search']}<span class="ph">Search merchant or amount</span></div>
  <div class="chips"><div class="chip active">This month</div><div class="chip accent">4 filters</div><div class="chip">SAR</div></div>
  <div class="list">{txn_row("Jarir Bookstore", "Other", "Card 4432", "SAR 1,249.00", "19:42", "var(--c4)", "J", "card", True)}</div>
</div>'''
cats = ["Groceries","Dining","Transport","Fuel","Utilities","Telecom","Rent","Health","Education","Shopping","Entertainment","Travel","Fees","Transfers","Other"]
grid = "".join(f'<div class="cat {"on" if c=="Shopping" else ""}">{c}</div>' for c in cats)
cat_sheet = f'''<div class="sheet" style="height:660px">
  <div class="grabber"></div>
  <div class="sheethead"><div><h2>Change category</h2><div class="sub">Jarir Bookstore · SAR 1,249.00</div></div><div class="close">{ICONS['close']}</div></div>
  <div style="display:flex;align-items:center;gap:8px;font-size:10px;color:var(--muted);margin-bottom:10px"><span class="pill accent">Current: Other · 62% confidence</span>{ICONS['chev']}<span class="pill primary">New: Shopping</span></div>
  <div class="input" style="margin-bottom:11px">{ICONS['search']}<span class="ph">Search 15 categories</span></div>
  <div class="catgrid">{grid}</div>
  <div class="notice" style="margin-top:12px"><div class="ni">{ICONS['info']}</div><div><div class="nt">This teaches the classifier</div><div class="ns">Next time Jarir Bookstore appears, it will be categorized as Shopping. You can undo this later.</div></div></div>
  <div class="spacer"></div>
  {button("Save correction")}
  <div class="btn ghost" style="height:40px;font-size:11.5px;margin-top:8px">Cancel</div>
</div>'''
body = f'<div class="sheetwrap">{detail_bg}<div class="backdrop"></div>{cat_sheet}</div>'
make("12-category", body)

# 13 Insights
body = f'''
<div class="content" style="gap:10px">
  {pagehead("Insights", "October 2026")}
  <div class="segmented"><div class="seg on">SAR</div><div class="seg">EGP</div></div>
  <div class="chips"><div class="chip active">This month</div><div class="chip">Last month</div><div class="chip">6 months</div></div>
  <div class="card pad">
    <div class="section-row"><div class="section">By category</div><div class="meta">SAR 4,820</div></div>
    <div style="display:flex;align-items:center;gap:14px;margin-top:8px">
      <div class="donut post" style="width:104px;height:104px;flex:0 0 104px"><div class="center"><b>4.8k</b><span>SAR</span></div></div>
      {cat_legend_insights}
    </div>
  </div>
  <div class="card pad">
    <div class="section-row"><div class="section">Cards vs Accounts</div><div class="meta">65% cards</div></div>
    <div style="height:14px;border-radius:99px;overflow:hidden;display:flex;margin-top:10px"><div style="width:65%;background:var(--primary)"></div><div style="width:35%;background:var(--accent)"></div></div>
    <div style="display:flex;justify-content:space-between;margin-top:7px;font-size:10px"><span><b>SAR 3,140</b> <span style="color:var(--muted)">Cards</span></span><span><b>SAR 1,680</b> <span style="color:var(--muted)">Accounts</span></span></div>
  </div>
  <div class="card pad">
    <div class="section-row"><div class="section">Monthly trend</div><div class="pill success">▲ 12%</div></div>
    <svg class="trend" style="height:96px" viewBox="0 0 380 118" fill="none" preserveAspectRatio="none">
      <path d="M0 92h380M0 62h380M0 32h380" stroke="#DCD5C7" stroke-width="1" stroke-dasharray="3 4"/>
      <path d="M10 86 74 74 138 80 202 58 266 66 330 34 370 42" stroke="#0E6B5C" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"/>
      <path d="M10 86 74 74 138 80 202 58 266 66 330 34 370 42V118H10Z" fill="#0E6B5C" opacity=".08"/>
      <circle cx="330" cy="34" r="4.5" fill="#0E6B5C" stroke="white" stroke-width="2"/>
    </svg>
    <div style="display:flex;justify-content:space-between;font-size:8.5px;color:var(--muted);margin-top:2px"><span>May</span><span>Jun</span><span>Jul</span><span>Aug</span><span>Sep</span><span>Oct</span></div>
  </div>
  <div class="card">
    <div class="pad" style="padding-bottom:6px"><div class="section">Top merchants</div></div>
    <div class="list compact" style="border:0;border-top:1px solid var(--outline);border-radius:0">
    {txn_row("Jarir Bookstore", "Shopping", "Card 4432", "SAR 1,249.00", "3 visits", "var(--c4)", "J")}
    {txn_row("Tamimi Markets", "Groceries", "Account 001", "SAR 612.00", "8 visits", "var(--c2)", "T", "bank")}
    {txn_row("Amazon Now", "Shopping", "Card 1214", "SAR 489.00", "6 visits", "var(--c1)", "A")}
    </div>
  </div>
</div>
{bottom_nav("insights")}'''
make("13-insights", body)

# 14 Settings
def setting(icon, title, sub, right=""):
    r = right if right else f'<div class="chev">{ICONS["chev"]}</div>'
    return f'''<div class="setting"><div class="si">{icon}</div><div><div class="st">{title}</div><div class="ss">{sub}</div></div>{r}</div>'''

body = f'''
<div class="content">
  {pagehead("Settings", "On this device")}
  <div class="notice"><div class="ni">{ICONS['warning']}</div><div class="grow"><div class="nt">6 card/account values need review</div><div class="ns">CIB, SNB and Riyad Bank</div></div><div class="textbtn">Review</div></div>
  <div class="setting-group">
    {setting(ICONS['bank'], "Banks & threads", "5 linked · 2,174 messages", '<div class="righttext">Manage</div>')}
    {setting(ICONS['card'], "Cards & accounts", "4 cards · 2 accounts · 6 to review", '<div class="righttext">Manage</div>')}
  </div>
  <div class="setting-group">
    {setting(ICONS['sms'], "Import new SMS", "Last scan 21 Oct, 14:30")}
    {setting(ICONS['activity'], "Rebuild from stored SMS", "Re-parse 2,174 messages")}
  </div>
  <div class="setting-group">
    {setting(ICONS['csv'], "Export CSV", "Last export 12 Oct 2026")}
    {setting(ICONS['cloud'], "Google Drive backup", "Off · optional encrypted backup", '<div class="righttext">Off</div>')}
  </div>
  <div class="setting-group">
    {setting(ICONS['shield'], "Privacy & data", "On-device only · no uploads")}
    {setting(ICONS['settings'], "Appearance", "System · light and dark ready", '<div class="righttext">System</div>')}
    {setting(ICONS['warning'], "Delete all data", "Remove SMS and transactions from this phone", '<div class="chev" style="color:var(--danger)">'+ICONS['chev']+'</div>')}
  </div>
  <div style="text-align:center;font-size:9px;color:var(--muted);margin-top:auto">SMSCostTracker 0.2.0 · offline build</div>
</div>
{bottom_nav("settings")}'''
make("14-settings", body)

# 15 Cards & accounts
def inst_row(digits, typ, txns, pending=False, hint=""):
    ch = ICONS["card"] if typ == "Card" else ICONS["bank"]
    status = f'<div class="pill accent" style="margin-left:auto">Review</div>' if pending else f'<div class="pill success" style="margin-left:auto">Confirmed</div>'
    return f'''<div class="row" style="min-height:52px">
      <div class="avatar-sm" style="background:var(--surface-alt);color:var(--primary)">{ch}</div>
      <div class="grow"><div class="name">{digits} <span style="font-weight:500;color:var(--muted);font-size:9px">· {typ}</span></div><div class="desc">{ch}{txns}{(" · "+hint) if hint else ""}</div></div>
      {status}
    </div>'''

body = f'''
<div class="content">
  {hdr("Cards & accounts", "Grouped by sender · 12 instruments")}
  <div class="card pad" style="display:flex;align-items:center;gap:12px"><div><div style="font-size:9px;color:var(--muted);font-weight:700;text-transform:uppercase;letter-spacing:.4px">Confirmed</div><div style="font-size:19px;font-weight:700;margin-top:2px">4 cards · 2 accounts</div></div><div style="margin-left:auto;text-align:right"><div class="pill accent">6 need review</div><div style="font-size:8.5px;color:var(--muted);margin-top:4px">Tap any row to change</div></div></div>
  <div class="grouphead"><div class="gh">SAB</div><div class="gc">4 values · 700 txns</div></div>
  <div class="list">
    {inst_row("1214", "Card", "318 txns", False)}
    {inst_row("001", "Account", "296 txns", False)}
    {inst_row("0542", "Card", "74 txns", False)}
    {inst_row("9981", "Card", "12 txns", False)}
  </div>
  <div class="grouphead"><div class="gh">Al Rajhi</div><div class="gc">2 values · 269 txns</div></div>
  <div class="list">
    {inst_row("4432", "Card", "241 txns", False)}
    {inst_row("8890", "Account", "28 txns", False)}
  </div>
  <div class="grouphead"><div class="gh">CIB</div><div class="gc">2 values · 121 txns</div></div>
  <div class="list">
    {inst_row("7788", "Card", "102 txns", True, "EGP card")}
    {inst_row("2201", "Account", "19 txns", True, "EGP account")}
  </div>
  <div class="grouphead"><div class="gh">SNB + Riyad Bank</div><div class="gc">4 values · 194 txns</div></div>
  <div class="list"><div class="row" style="min-height:44px"><div class="grow" style="font-size:10.5px;color:var(--muted)">4 more values from 2 senders</div><div class="textbtn">Show all</div></div></div>
</div>'''
make("15-instruments", body)

# 16 Export & backup
body = f'''
<div class="content">
  {hdr("Export & backup", "You choose what leaves this phone")}
  <div class="card pad">
    <div style="display:flex;align-items:center;gap:10px"><div class="si" style="width:38px;height:38px;border-radius:12px;background:color-mix(in srgb, var(--primary) 11%, var(--surface));color:var(--primary);display:grid;place-items:center">{ICONS['csv']}</div><div><div style="font-size:13px;font-weight:700">Export transactions</div><div style="font-size:9.5px;color:var(--muted);margin-top:2px">CSV · 1,284 transactions · SAR + EGP</div></div></div>
    <div style="font-size:10px;line-height:1.45;color:var(--muted);margin-top:10px">Dates, merchants, categories, amounts, currencies and instruments. No SMS text is included.</div>
    <div style="display:flex;gap:8px;margin-top:11px">{button("Export CSV", "btn small")}<div class="btn ghost small" style="flex:1">Share summary</div></div>
    <div style="font-size:8.5px;color:var(--muted);margin-top:8px">Last export: 12 Oct 2026 · 1,102 rows</div>
  </div>
  <div class="card pad">
    <div style="display:flex;align-items:center;gap:10px"><div class="si" style="width:38px;height:38px;border-radius:12px;background:color-mix(in srgb, var(--accent) 13%, var(--surface));color:var(--accent);display:grid;place-items:center">{ICONS['cloud']}</div><div><div style="font-size:13px;font-weight:700">Google Drive backup</div><div style="font-size:9.5px;color:var(--muted);margin-top:2px">Optional encrypted backup</div></div><div class="pill" style="margin-left:auto">Off</div></div>
    <div style="font-size:10px;line-height:1.45;color:var(--muted);margin-top:10px">Back up the database so you can restore it on a new phone. You choose when to upload.</div>
    <div style="margin-top:11px">{button("Connect Google Drive", "btn ghost small")}</div>
  </div>
  <div class="card pad">
    <div class="section" style="margin-bottom:7px">What leaves your phone?</div>
    <div class="why">
      <div class="item"><span class="tick">{ICONS['check']}</span><span>Only the CSV or backup file you explicitly create.</span></div>
      <div class="item"><span class="tick">{ICONS['check']}</span><span>SMS text stays on this device and is never uploaded.</span></div>
      <div class="item"><span class="tick">{ICONS['check']}</span><span>Drive backup is encrypted before it leaves the app.</span></div>
    </div>
  </div>
  <div class="notice" style="margin-top:auto"><div class="ni">{ICONS['info']}</div><div><div class="nt">Need a fresh parse?</div><div class="ns">Use Settings → Rebuild from stored SMS after a parser update. Nothing is downloaded.</div></div></div>
</div>'''
make("16-export", body)

print("generated", len(list(OUT.glob('*.html'))), "screens")
