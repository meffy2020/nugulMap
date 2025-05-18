import React, { useState, useEffect } from "react";
import MainPage from "./components/mainPage";
import LoadingPage from "./components/LoadingPage"; // 로딩 페이지

function App() {
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    // 3초 후 로딩 상태를 false로 변경
    const timer = setTimeout(() => {
      setIsLoading(false);
    }, 4000); // 3초

    // 클린업 함수: 컴포넌트가 언마운트될 때 타이머 제거
    return () => clearTimeout(timer);
  }, []);

  return (
    <div className="App">
      {isLoading ? <LoadingPage /> : <MainPage />}
    </div>
  );
}

export default App;

document.cookie = "myCookie=myValue; Partitioned; Secure; SameSite=None";