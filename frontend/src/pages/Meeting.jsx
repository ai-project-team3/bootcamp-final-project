// Owner: 역할 1. 화면 ② — 미팅 중 (본체)
import Cards from "../components/meeting/Cards.jsx";
import Checklist from "../components/meeting/Checklist.jsx";
import Objections from "../components/meeting/Objections.jsx";
import TopBar from "../components/meeting/TopBar.jsx";

export default function Meeting() {
  return (
    <main className="page">
      <TopBar />
      <div className="meeting-grid">
        <Objections />
        <Checklist />
        <Cards />
      </div>
    </main>
  );
}
