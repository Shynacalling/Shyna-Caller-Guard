const express = require('express');
const { AccessToken } = require('livekit-server-sdk');
const admin = require('firebase-admin');

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 10000;

function firebaseReady() {
  return admin.apps && admin.apps.length > 0;
}

// Initialize Firebase Admin from Render/hosting environment.
if (process.env.FIREBASE_SERVICE_ACCOUNT) {
  try {
    const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
    admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
    console.log('Firebase Admin initialized');
  } catch (err) {
    console.error('Failed to initialize Firebase Admin:', err);
  }
} else {
  console.warn('FIREBASE_SERVICE_ACCOUNT is not configured');
}

async function verifyToken(req, res, next) {
  if (!firebaseReady()) {
    return res.status(503).json({ error: 'Firebase Admin is not configured on the server' });
  }

  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Unauthorized' });
  }

  const idToken = authHeader.substring('Bearer '.length).trim();
  try {
    req.user = await admin.auth().verifyIdToken(idToken);
    next();
  } catch (err) {
    console.error('Verify token failed:', err);
    return res.status(401).json({ error: 'Unauthorized' });
  }
}

app.get('/health', (req, res) => {
  res.json({
    ok: true,
    firebaseConfigured: firebaseReady(),
    livekitConfigured: Boolean(process.env.LIVEKIT_API_KEY && process.env.LIVEKIT_API_SECRET)
  });
});

function isTerminalStatus(status) {
  return ['ENDED', 'MISSED', 'DECLINED', 'CANCELLED', 'FAILED', 'BUSY', 'NO_ANSWER', 'UNAVAILABLE', 'REJECTED'].includes(status);
}

function timestampToMillis(value) {
  if (!value) return null;
  if (typeof value === 'number') return value;
  if (value.toMillis) return value.toMillis();
  if (value._seconds) return value._seconds * 1000;
  return null;
}

async function writeCallHistory(callData, statusOverride) {
  if (!callData || String(callData.id || '').startsWith('MEETING_') || callData.receiverUid === 'MEETING_ROOM') return;

  const status = statusOverride || callData.status || 'RINGING';
  const connectedMs = timestampToMillis(callData.connectedAt) || timestampToMillis(callData.answeredAt);
  const endedMs = timestampToMillis(callData.endedAt) || (isTerminalStatus(status) ? Date.now() : null);
  const duration = connectedMs && endedMs ? Math.max(0, Math.floor((endedMs - connectedMs) / 1000)) : Number(callData.duration || 0);

  const base = {
    callId: callData.id,
    callerUid: callData.callerUid || '',
    callerName: callData.callerName || 'Shyna User',
    callerPhoto: callData.callerPhoto || null,
    receiverUid: callData.receiverUid || '',
    receiverName: callData.receiverName || 'Shyna User',
    receiverPhoto: callData.receiverPhoto || null,
    type: callData.type || 'VOICE',
    status,
    duration,
    timestamp: admin.firestore.Timestamp.now()
  };

  const writes = [];
  if (callData.callerUid) {
    writes.push(admin.firestore().collection('users').doc(callData.callerUid)
      .collection('call_history').doc(callData.id)
      .set({ ...base, direction: 'outgoing' }, { merge: true }));
  }
  if (callData.receiverUid && !['GROUP', 'MEETING_ROOM'].includes(callData.receiverUid)) {
    writes.push(admin.firestore().collection('users').doc(callData.receiverUid)
      .collection('call_history').doc(callData.id)
      .set({ ...base, direction: 'incoming' }, { merge: true }));
  }
  await Promise.allSettled(writes);
}

async function sendCallFcm(receiverUid, payload) {
  if (!firebaseReady() || !receiverUid || ['GROUP', 'MEETING_ROOM'].includes(receiverUid)) return;
  try {
    const userDoc = await admin.firestore().collection('users').doc(receiverUid).get();
    const fcmToken = userDoc.data()?.fcmToken;
    if (!fcmToken) return;

    const data = {};
    Object.entries(payload || {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null) data[key] = String(value);
    });

    await admin.messaging().send({
      data,
      token: fcmToken,
      android: { priority: 'high', ttl: 0 }
    });
    console.log(`FCM sent to ${receiverUid} type=${data.type || 'UNKNOWN'}`);
  } catch (err) {
    console.error(`FCM send failed for ${receiverUid}:`, err);
  }
}

// LiveKit token endpoint.
app.post('/token', verifyToken, async (req, res) => {
  const { roomName, participantName, callId } = req.body;
  const uid = req.user.uid;
  if (!roomName || !callId) return res.status(400).json({ error: 'roomName and callId are required' });

  try {
    const callDoc = await admin.firestore().collection('app_calls').doc(callId).get();
    if (!callDoc.exists) return res.status(404).json({ error: 'Call not found' });

    const callData = callDoc.data();
    const participants = Array.isArray(callData.participantIds) ? callData.participantIds : [];
    const isParticipant = callData.callerUid === uid || callData.receiverUid === uid || participants.includes(uid);
    if (!isParticipant) return res.status(403).json({ error: 'Unauthorized: not a call participant' });
    if (isTerminalStatus(callData.status)) return res.status(410).json({ error: 'Call/meeting has ended' });

    const apiKey = process.env.LIVEKIT_API_KEY;
    const apiSecret = process.env.LIVEKIT_API_SECRET;
    if (!apiKey || !apiSecret) return res.status(500).json({ error: 'LiveKit credentials not configured on server' });

    const at = new AccessToken(apiKey, apiSecret, {
      identity: uid,
      name: participantName || uid,
      ttl: '1h'
    });
    at.addGrant({ roomJoin: true, room: roomName, canPublish: true, canSubscribe: true });
    const token = await at.toJwt();
    return res.json({ token, roomName, identity: uid });
  } catch (err) {
    console.error('LiveKit token creation failed:', err);
    return res.status(500).json({ error: 'Failed to create LiveKit token' });
  }
});

// Create one-to-one or group call.
app.post('/api/calls/create', verifyToken, async (req, res) => {
  const { receiverUid, type, isGroup, participantIds } = req.body;
  const callerUid = req.user.uid;
  const callId = `call_${Date.now()}_${Math.floor(Math.random() * 100000)}`;
  const roomName = `room_${callId}`;

  try {
    const callerDoc = await admin.firestore().collection('users').doc(callerUid).get();
    const callerData = callerDoc.data() || {};
    let receiverData = {};
    if (receiverUid && !['GROUP', 'MEETING_ROOM'].includes(receiverUid)) {
      const receiverDoc = await admin.firestore().collection('users').doc(receiverUid).get();
      if (!receiverDoc.exists) return res.status(404).json({ error: 'Receiver not found' });
      receiverData = receiverDoc.data() || {};
    }

    const participants = Array.from(new Set([
      callerUid,
      ...(Array.isArray(participantIds) ? participantIds : []),
      ...(!isGroup && receiverUid ? [receiverUid] : [])
    ].filter(Boolean)));

    if (participants.length < 2) return res.status(400).json({ error: 'At least two participants are required' });

    const callData = {
      id: callId,
      callerUid,
      callerName: callerData.name || req.user.name || 'Shyna User',
      callerPhoto: callerData.photoUrl || null,
      receiverUid: isGroup ? 'GROUP' : receiverUid,
      receiverName: isGroup ? 'Group Call' : (receiverData.name || 'Shyna User'),
      receiverPhoto: isGroup ? null : (receiverData.photoUrl || null),
      type: type === 'VIDEO' ? 'VIDEO' : 'VOICE',
      status: 'RINGING',
      roomName,
      timestamp: Date.now(),
      participantIds: participants,
      isGroup: Boolean(isGroup),
      lastUpdatedAt: Date.now(),
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    };

    await admin.firestore().collection('app_calls').doc(callId).set(callData);
    await writeCallHistory(callData, 'RINGING');

    await Promise.all(participants
      .filter(id => id !== callerUid)
      .map(id => sendCallFcm(id, {
        type: 'INCOMING_CALL',
        callId,
        callerUid,
        callerName: callData.callerName,
        callType: callData.type
      })));

    return res.json({ success: true, callId, roomName });
  } catch (err) {
    console.error('Create call failed:', err);
    return res.status(500).json({ error: 'Failed to create call' });
  }
});

async function handleCallAction(req, res, targetStatus) {
  const { callId } = req.params;
  const uid = req.user.uid;
  try {
    const ref = admin.firestore().collection('app_calls').doc(callId);
    const doc = await ref.get();
    if (!doc.exists) return res.status(404).json({ error: 'Call not found' });
    const call = doc.data();
    const participants = Array.isArray(call.participantIds) ? call.participantIds : [];
    if (![call.callerUid, call.receiverUid].includes(uid) && !participants.includes(uid)) {
      return res.status(403).json({ error: 'Not a call participant' });
    }

    const now = Date.now();
    const updates = { status: targetStatus, lastUpdatedAt: now };
    if (targetStatus === 'ACCEPTED') {
      updates.answeredAt = now;
    }
    if (isTerminalStatus(targetStatus)) updates.endedAt = now;
    await ref.update(updates);

    const updatedCall = { ...call, ...updates, id: callId };
    await writeCallHistory(updatedCall, targetStatus);

    const notifyTargets = participants.filter(id => id !== uid);
    await Promise.all(notifyTargets.map(id => sendCallFcm(id, {
      type: `CALL_${targetStatus}`,
      callId,
      actedBy: uid
    })));

    return res.json({ success: true, status: targetStatus });
  } catch (err) {
    console.error(`Call action ${targetStatus} failed:`, err);
    return res.status(500).json({ error: `Failed to set call ${targetStatus}` });
  }
}

app.post('/api/calls/:callId/accept', verifyToken, (req, res) => handleCallAction(req, res, 'ACCEPTED'));
app.post('/api/calls/:callId/decline', verifyToken, (req, res) => handleCallAction(req, res, 'DECLINED'));
app.post('/api/calls/:callId/cancel', verifyToken, (req, res) => handleCallAction(req, res, 'CANCELLED'));
app.post('/api/calls/:callId/end', verifyToken, (req, res) => handleCallAction(req, res, 'ENDED'));
app.post('/api/calls/:callId/missed', verifyToken, (req, res) => handleCallAction(req, res, 'MISSED'));
app.post('/api/calls/:callId/no-answer', verifyToken, (req, res) => handleCallAction(req, res, 'NO_ANSWER'));
app.post('/api/calls/:callId/busy', verifyToken, (req, res) => handleCallAction(req, res, 'BUSY'));

// Meeting join validation and participant registration.
app.post('/api/meetings/join', verifyToken, async (req, res) => {
  const uid = req.user.uid;
  const meetingId = String(req.body.meetingId || '').replace(/[^a-zA-Z0-9]/g, '');
  const passcode = String(req.body.passcode || '');
  if (!meetingId) return res.status(400).json({ error: 'Meeting ID is required' });

  try {
    const snap = await admin.firestore().collection('meetings').where('meetingId', '==', meetingId).limit(1).get();
    if (snap.empty) return res.status(404).json({ error: 'Invalid Meeting ID' });
    const meetingDoc = snap.docs[0];
    const meeting = meetingDoc.data();
    if (meeting.status !== 'LIVE') return res.status(410).json({ error: meeting.status === 'ENDED' ? 'Meeting Ended' : 'Meeting Not Started' });
    if (meeting.passcode && meeting.passcode !== passcode) return res.status(403).json({ error: 'Incorrect Password' });

    const callId = `MEETING_${meetingId}`;
    const callRef = admin.firestore().collection('app_calls').doc(callId);
    const callDoc = await callRef.get();
    if (!callDoc.exists) return res.status(409).json({ error: 'Meeting room is not active yet' });

    await callRef.update({ participantIds: admin.firestore.FieldValue.arrayUnion(uid), lastUpdatedAt: Date.now() });
    await meetingDoc.ref.update({ invitees: admin.firestore.FieldValue.arrayUnion(uid), updatedAt: Date.now() });
    return res.json({ success: true, callId, meetingId });
  } catch (err) {
    console.error('Join meeting failed:', err);
    return res.status(500).json({ error: 'Unable to join meeting' });
  }
});

app.post('/api/scheduled-calls', verifyToken, async (req, res) => {
  const { title, description, participantIds, type, scheduledFor } = req.body;
  const organizerId = req.user.uid;
  try {
    const scheduleData = {
      title,
      description: description || '',
      organizerId,
      participantIds: participantIds || [organizerId],
      type: type || 'VOICE',
      scheduledFor: admin.firestore.Timestamp.fromDate(new Date(scheduledFor)),
      status: 'scheduled',
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    };
    const docRef = await admin.firestore().collection('scheduled_calls').add(scheduleData);
    await Promise.all(scheduleData.participantIds.filter(id => id !== organizerId).map(id => sendCallFcm(id, {
      type: 'CALL_SCHEDULED', scheduleId: docRef.id, title, scheduledFor: String(scheduledFor)
    })));
    return res.json({ success: true, id: docRef.id });
  } catch (err) {
    return res.status(500).json({ error: 'Failed to schedule call' });
  }
});

// LiveKit webhook. Configure signature verification in hosting before public production use.
app.post('/webhooks/livekit', async (req, res) => {
  if (!firebaseReady()) return res.sendStatus(503);
  const event = req.body;
  try {
    const roomName = event.room?.name;
    if (!roomName) return res.sendStatus(200);
    const callId = roomName.replace(/^room_/, '');
    const callRef = admin.firestore().collection('app_calls').doc(callId);
    if (event.event === 'participant_joined') {
      await callRef.update({ status: 'CONNECTED', connectedAt: Date.now(), lastUpdatedAt: Date.now() });
    } else if (event.event === 'room_finished') {
      await callRef.update({ status: 'ENDED', endedAt: Date.now(), lastUpdatedAt: Date.now() });
      const doc = await callRef.get();
      if (doc.exists) await writeCallHistory({ ...doc.data(), id: callId }, 'ENDED');
    }
  } catch (err) {
    console.error('Webhook processing failed:', err);
  }
  return res.sendStatus(200);
});

// Kept for older Android builds that call this endpoint explicitly.
app.post('/notify-call', verifyToken, async (req, res) => {
  const { callId, receiverUid, callerUid, callerName, callType } = req.body;
  if (!callId || !receiverUid || !callerName || !callType) return res.status(400).json({ error: 'Missing required fields' });
  await sendCallFcm(receiverUid, { type: 'INCOMING_CALL', callId, receiverUid, callerUid, callerName, callType });
  return res.json({ success: true });
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Shyna token/signaling server listening on ${PORT}`);
});
