const express = require("express");
const fs = require("fs");
const path = require("path");
const multer = require("multer");
const { exec } = require("child_process");
const bodyParser = require("body-parser");
const cors = require("cors");
require('dotenv').config();

const app = express();
const PORT = process.env.PORT || 3001;
const FASTAPI_URL = process.env.FASTAPI_URL || "http://127.0.0.1:8000/STT";
const recordingState = new Map();

// 환경변수 확인 로그 추가
// console.log('🔥 환경변수 확인:');
// console.log('OPENAI_API_KEY:', process.env.OPENAI_API_KEY ? '설정됨' : '없음');
// console.log('API 키 시작:', process.env.OPENAI_API_KEY ? process.env.OPENAI_API_KEY.substring(0, 10) + '...' : 'None');
// console.log('PORT:', process.env.PORT);

// CORS 및 JSON 파싱 설정
app.use(cors());
app.use(express.json({ limit: '10mb' }));

const AUDIO_BASE_PATH=path.join(__dirname, "audio");


//현재 __dirpath  C:\workspace\fronted\edumeet_frontend\backend    
console.log("현재 __dirpath " , __dirname);
//console.log("현재 AUDIO_BASE_PATH" , AUDIO_BASE_PATH);

// AUDIO_BASE_PATH C:\workspace\fronted\edumeet_frontend\backend\audio

// 수업 시작 버튼 클릭시 start-recording 호출
app.post("/api/class/:classId/start-recording", (req, res) => {
  const { classId } = req.params;
  const { className, creatorName, startTime, meetingId } = req.body;
  
  console.log("📝 start-recording 받은 데이터:");
  console.log("- classId:", classId);
  console.log("- className:", className);
  console.log("- creatorName:", creatorName);
  console.log("- startTime:", startTime);
  console.log("- meetingId:", meetingId);
  
  const classDir = path.join(AUDIO_BASE_PATH, classId);
  console.log("classDir 입니다 : ", classDir);
  fs.mkdirSync(classDir, { recursive: true });

  recordingState.set(classId, {recording:true, paused : false});
  console.log(`[start] class=${classId} -> recording=true, paused=false`);

  return res.status(200).json({ message: "녹음 저장 시작 준비 완료" });
});


// 일시정지: 업로드는 받되 버리거나(권장) 아예 거부
app.post("/api/class/:classId/pause-recording", (req, res) => {
  const { classId } = req.params;
  const st = recordingState.get(classId);
  if (!st || !st.recording) {
    return res.status(409).json({ message: "녹화 세션이 없습니다." });
  }
  st.paused = true;
  console.log(`[pause] class=${classId} -> paused=true`);
  return res.status(200).json({ message: "일시정지되었습니다." });
});


// 재개: 저장 재허용
app.post("/api/class/:classId/resume-recording", (req, res) => {
  const { classId } = req.params;
  const st = recordingState.get(classId);
  if (!st || !st.recording) {
    return res.status(409).json({ message: "녹화 세션이 없습니다." });
  }
  st.paused = false;
  console.log(`[resume] class=${classId} -> paused=false`);
  return res.status(200).json({ message: "재개되었습니다." });
});


// 상태 체크 미들웨어 (녹화 중/일시정지 차단)
function ensureWritable(req, res, next) {
  const { classId } = req.params;
  const st = recordingState.get(classId);
  if (!st || !st.recording) {
    return res.status(423).json({ message: '세션 종료(또는 요약 중): 업로드 불가' });
  }
  if (st.paused) {
    return res.status(202).json({ message: '일시정지 중: 저장하지 않음' });
  }
  next();
}

const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    const classId = req.params.classId;
    const dir = path.join(AUDIO_BASE_PATH, classId);
    console.log("dir : " , dir);
    fs.mkdirSync(dir, { recursive: true });
    cb(null, dir);
  },
  filename: function (req, file, cb) {
    const classId = req.params.classId;
    const dir = path.join(AUDIO_BASE_PATH, classId);
    console.log("dir : " , dir);
    const files = fs
      .readdirSync(dir)
      .filter((f) => f.startsWith("audio_") && f.endsWith(".wav"));
    const nextChunk = files.length + 1;
    cb(null, `audio_${nextChunk}.wav`);
  },
});



const upload = multer({ storage: storage });

app.post('/api/class/:classId/update-recording', ensureWritable ,upload.single('audio'), (req, res) => {
  // 저장 성공
  if (!req.file) return res.status(400).json({ message: "No audio file uploaded." });
  console.log(`✅ 저장됨: /audio/${req.params.classId}/${req.file.filename}`);
  return res.status(200).json({ message: 'Chunk saved successfully.', filename: req.file.filename });
});



app.post("/api/class/:classId/stop-recording", async (req, res) => {
  try{
    console.log("백엔드 stop-recording 요청 받은 후 문서 요약 실행 시작");
    const { classId } = req.params;
    const {mettingId} 


    //const pyPath = path.join(__dirname, "test_nodejs_api.py");
    const classDir = path.join(AUDIO_BASE_PATH, classId);
    //const command = `python3 ${pyPath} ${classDir} ${totalChunks}`;

    // 업로드 차단
    const st = recordingState.get(classId) || {};
    recordingState.set(classId, { recording: false, paused: !!st.paused });
    console.log(`[stop] class=${classId} -> recording=false`);

    //console.log("pyPath : ", pyPath);
    console.log("[stop-recording] classDir : ",classDir);
    console.log("[stop-recording] sending to FastAPI : " , FASTAPI_URL);
    //console.log("command : ", command);

    // FastAPI로 POST
    const payload = {
      class_dir: classDir,
      meeting_id: meetingId, // meetingId 추가
      total_chunks: totalChunks, // totalChunks 추가
      generate_summary: generateSummary // generateSummary 추가
    };

    // fast api 호출
    const resp = await fetch(`${FASTAPI_URL}/${classId}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });

    if (!resp.ok) {
      const text = await resp.text().catch(() => "");
      console.error("FastAPI error:", resp.status, text);
      return res.status(502).json({ error: "FastAPI 요청 실패", detail: text });
    }

    // 3) FastAPI 응답 JSON 파싱
    const data = await resp.json();

    // 4) Vue 응답 전송
    res.status(202).json({
      message: "처리 시작",
      recordingStopped: true,
      job: data, // {job_id, status, ...}
    });

   // 응답 후 정리(선택)
    res.on("finish", () => {
      try {
        if (fs.existsSync(classDir)) {
          fs.rmSync(classDir, { recursive: true, force: true });
          console.log(`🗑️  ${classDir} 폴더 삭제 완료`);
        }
      } catch (delErr) {
        console.error(`⚠️  ${classDir} 폴더 삭제 중 오류:`, delErr);
      }
    });


    }catch (e) {
      return res.status(500).json({ error: "서버 내부 오류", detail: String(e) });
    }

});


app.listen(PORT, () => {
  console.log(`🚀 Server running at http://localhost:${PORT}`);

}); 