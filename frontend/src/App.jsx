import { Route, Routes } from "react-router-dom";

import DealSelect from "./pages/DealSelect.jsx";
import Meeting from "./pages/Meeting.jsx";
import Report from "./pages/Report.jsx";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<DealSelect />} />
      <Route path="/meeting/:meetingId" element={<Meeting />} />
      <Route path="/report/:meetingId" element={<Report />} />
    </Routes>
  );
}
