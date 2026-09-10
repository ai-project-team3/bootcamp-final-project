// Owner: 역할 1.
// One socket carries transcript, state, cards and backchannel together -
// a single channel gives message ordering for free.
export function connectMeeting(meetingId, sessionId, { onMessage, onClose } = {}) {
  const proto = location.protocol === "https:" ? "wss" : "ws";
  const socket = new WebSocket(
    `${proto}://${location.host}/ws/meeting/${meetingId}?session_id=${sessionId}`,
  );
  socket.onmessage = (e) => onMessage?.(JSON.parse(e.data));
  socket.onclose = (e) => onClose?.(e);
  return socket;
}
