import React, { useEffect, useState } from 'react';
import './LoadingPage.css';

const LoadingPage = () => {
  const [progress, setProgress] = useState(0);

  useEffect(() => {
    const interval = setInterval(() => {
      setProgress((prev) => {
        if (prev >= 100) {
          clearInterval(interval);
          return 100;
        }
        return prev + 1; // 1%씩 증가
      });
    }, 30); // 30ms마다 업데이트 (3초 동안 100%)
  }, []);

  return (
    <div className="loading-container">
      <img 
        src="./logo.jpg" // 로고 이미지
        alt="Logo" 
        className="loading-logo" 
      />
      <img 
        src="./너구리.gif" // 로고 이미지
        alt="Logo" 
        className="loading-logo" 
      />
      <div className="progress-bar">
        <div 
          className="progress-fill" 
          style={{ width: `${progress}%` }}
        />
      </div>
      <h5 style={{ textAlign: "center", margin: "10px 0" }}>{progress}%</h5>
    </div>
  );
};

export default LoadingPage;