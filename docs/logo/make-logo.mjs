// Generates the mod icon: node docs/logo/make-logo.mjs neoforge-1.21.1/src/main/resources/logo.png docs/logo/logo-512.png
import fs from 'node:fs'; import zlib from 'node:zlib';
const N=32; const px=Array.from({length:N*N},()=>[0,0,0,0]);
const set=(x,y,c)=>{ if(x>=0&&y>=0&&x<N&&y<N) px[y*N+x]=c; };
const hex=h=>[parseInt(h.slice(1,3),16),parseInt(h.slice(3,5),16),parseInt(h.slice(5,7),16),255];
let seed=7; const rnd=()=>{seed=(seed*1103515245+12345)&0x7fffffff; return seed/0x7fffffff;};
// background
for(let y=0;y<N;y++)for(let x=0;x<N;x++) set(x,y,hex(((x+y)&1)?'#23272e':'#252a31'));
// 3x3 stone blocks, 8px each with 1px dark seams, area 3..28
const stone=['#8a8a8a','#7d7d7d','#959595','#6f6f6f'];
for(let by=0;by<3;by++)for(let bx=0;bx<3;bx++){
  const ox=4+bx*8, oy=4+by*8, centre=(bx===1&&by===1);
  for(let y=0;y<8;y++)for(let x=0;x<8;x++){
    let c = x===7||y===7 ? '#4a4a4a' : stone[Math.floor(rnd()*4)];
    if(centre) c = x===7||y===7 ? '#7a5a10' : ['#e0b020','#c99a18','#f0c840','#b88a14'][Math.floor(rnd()*4)];
    set(ox+x,oy+y,hex(c));
  }
}
// gold outline around the whole 3x3 area
for(let i=3;i<=28;i++){ set(i,3,hex('#e0b020')); set(i,28,hex('#e0b020')); set(3,i,hex('#e0b020')); set(28,i,hex('#e0b020')); }
// pickaxe: handle on the diagonal x+y=33, head arc symmetric about it
const dark=hex('#1a1a1a'), wood=hex('#9c6a35'), woodD=hex('#5e3a17'), head=hex('#6ee6e0'), headD=hex('#2fa8a2'), headL=hex('#d8fffc');
for(let x=7;x<=21;x++){ const y=33-x; set(x-1,y,dark); set(x,y+1,dark); }
for(let x=7;x<=21;x++){ const y=33-x; set(x,y,wood); set(x+1,y,woodD); }
const arc=[[13,12],[14,11],[15,10],[16,9],[17,8],[18,8],[19,8],[20,9],[21,10],[22,11],[23,12],[24,13],[25,14],[25,15],[25,16],[24,17],[23,18],[22,19],[21,20]];
for(const [x,y] of arc){ set(x+1,y-1,dark); set(x,y-1,dark); set(x+1,y,dark); }
for(const [x,y] of arc.slice(1,-1)){ set(x-1,y+1,headD); }
for(const [x,y] of arc.slice(5,-5)){ set(x-2,y+2,headD); }
for(const [x,y] of arc){ set(x,y,head); }
set(12,13,dark); set(13,13,dark); set(12,12,dark); set(21,21,dark); set(20,21,dark); set(20,20,dark);
[[18,8],[21,10],[22,11],[25,15]].forEach(([x,y])=>set(x,y,headL));
// encode PNG scaled
function png(scale,file){
  const W=N*scale; const raw=Buffer.alloc((W*4+1)*W);
  for(let y=0;y<W;y++){ raw[y*(W*4+1)]=0; for(let x=0;x<W;x++){ const c=px[Math.floor(y/scale)*N+Math.floor(x/scale)]; raw.set(c,y*(W*4+1)+1+x*4);} }
  const crcT=new Int32Array(256).map((_,n)=>{let c=n;for(let k=0;k<8;k++)c=c&1?0xedb88320^(c>>>1):c>>>1;return c;});
  const crc=b=>{let c=-1;for(const v of b)c=crcT[(c^v)&255]^(c>>>8);return (c^-1)>>>0;};
  const chunk=(t,d)=>{const l=Buffer.alloc(4);l.writeUInt32BE(d.length);const td=Buffer.concat([Buffer.from(t),d]);const c=Buffer.alloc(4);c.writeUInt32BE(crc(td));return Buffer.concat([l,td,c]);};
  const ih=Buffer.alloc(13); ih.writeUInt32BE(W,0); ih.writeUInt32BE(W,4); ih[8]=8; ih[9]=6;
  fs.writeFileSync(file,Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',ih),chunk('IDAT',zlib.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]));
}
png(8, process.argv[2]); png(16, process.argv[3]);
