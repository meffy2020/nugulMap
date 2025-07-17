
import React, { useState, useEffect } from 'react';
import './AdminPage.css'; // CSS 파일 임포트

const API_URL = 'https://port-0-nugulmap-mat8aw6t11657452.sel4.cloudtype.app';

function AdminPage() {
    const [markers, setMarkers] = useState([]);
    const [formData, setFormData] = useState({});
    const [isEditing, setIsEditing] = useState(false);
    const [currentMarkerId, setCurrentMarkerId] = useState(null);

    // Fetch all markers
    const fetchMarkers = async () => {
        try {
            const response = await fetch(`${API_URL}/marker`);
            if (!response.ok) {
                throw new Error('Failed to fetch markers');
            }
            const data = await response.json();
            setMarkers(data.markers);
        } catch (error) {
            console.error("Error fetching markers:", error);
            alert("마커를 불러오는 데 실패했습니다.");
        }
    };

    useEffect(() => {
        fetchMarkers();
    }, []);

    const handleInputChange = (e) => {
        const { name, value } = e.target;
        setFormData({ ...formData, [name]: value });
    };

    const handleFormSubmit = async (e) => {
        e.preventDefault();
        const method = isEditing ? 'PUT' : 'POST';
        const url = isEditing ? `${API_URL}/marker/${currentMarkerId}` : `${API_URL}/marker`;
        
        // Convert to correct types
        const body = {
            ...formData,
            latitude: parseFloat(formData.latitude),
            longitude: parseFloat(formData.longitude),
            capacity: parseInt(formData.capacity, 10) || null,
            rating: parseFloat(formData.rating) || null,
        };

        try {
            const response = await fetch(url, {
                method: method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body),
            });

            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.detail || 'API request failed');
            }

            alert(`마커가 성공적으로 ${isEditing ? '수정' : '생성'}되었습니다.`);
            resetForm();
            fetchMarkers();

        } catch (error) {
            console.error("Error submitting form:", error);
            alert(`오류: ${error.message}`);
        }
    };

    const handleEdit = (marker) => {
        setIsEditing(true);
        setCurrentMarkerId(marker.id);
        setFormData({
            name: marker.name || '',
            latitude: marker.latitude || '',
            longitude: marker.longitude || '',
            description: marker.description || '',
            address: marker.address || '',
            status: marker.status || '운영 중',
        });
    };

    const handleDelete = async (markerId) => {
        if (window.confirm("정말로 이 마커를 삭제하시겠습니까?")) {
            try {
                const response = await fetch(`${API_URL}/marker/${markerId}`, {
                    method: 'DELETE',
                });
                if (!response.ok) {
                    throw new Error('Failed to delete marker');
                }
                alert("마커가 삭제되었습니다.");
                fetchMarkers();
            } catch (error) {
                console.error("Error deleting marker:", error);
                alert("마커 삭제에 실패했습니다.");
            }
        }
    };

    const resetForm = () => {
        setIsEditing(false);
        setCurrentMarkerId(null);
        setFormData({
            name: '',
            latitude: '',
            longitude: '',
            description: '',
            address: '',
            status: '운영 중',
        });
    };

    return (
        <div className="admin-container">
            <h1 className="admin-header">너굴맵 어드민 페이지</h1>

            {/* Form for Create/Update */}
            <form onSubmit={handleFormSubmit} className="admin-form-section">
                <h3>{isEditing ? '마커 수정' : '새 마커 생성'}</h3>
                <div className="admin-form-group">
                    <label htmlFor="name">이름 (필수)</label>
                    <input id="name" name="name" value={formData.name || ''} onChange={handleInputChange} placeholder="마커 이름" required type="text"/>
                </div>
                <div className="admin-form-group">
                    <label htmlFor="latitude">위도 (필수)</label>
                    <input id="latitude" name="latitude" value={formData.latitude || ''} onChange={handleInputChange} placeholder="예: 37.5" required type="number" step="any"/>
                </div>
                <div className="admin-form-group">
                    <label htmlFor="longitude">경도 (필수)</label>
                    <input id="longitude" name="longitude" value={formData.longitude || ''} onChange={handleInputChange} placeholder="예: 127.0" required type="number" step="any"/>
                </div>
                <div className="admin-form-group">
                    <label htmlFor="description">설명</label>
                    <textarea id="description" name="description" value={formData.description || ''} onChange={handleInputChange} placeholder="마커에 대한 상세 설명"></textarea>
                </div>
                <div className="admin-form-group">
                    <label htmlFor="address">주소</label>
                    <input id="address" name="address" value={formData.address || ''} onChange={handleInputChange} placeholder="주소" type="text"/>
                </div>
                <div className="admin-form-group">
                    <label htmlFor="status">상태</label>
                    <input id="status" name="status" value={formData.status || ''} onChange={handleInputChange} placeholder="예: 운영 중, 폐쇄" type="text"/>
                </div>
                <div className="admin-form-actions">
                    <button type="submit">{isEditing ? '수정하기' : '생성하기'}</button>
                    {isEditing && <button type="button" onClick={resetForm}>취소</button>}
                </div>
            </form>

            {/* Marker List */}
            <div className="admin-table-section">
                <h2>마커 목록</h2>
                <table className="admin-table">
                    <thead>
                        <tr>
                            <th>이름</th>
                            <th>위도</th>
                            <th>경도</th>
                            <th>주소</th>
                            <th>상태</th>
                            <th>작업</th>
                        </tr>
                    </thead>
                    <tbody>
                        {markers.map(marker => (
                            <tr key={marker.id}>
                                <td>{marker.name}</td>
                                <td>{marker.latitude}</td>
                                <td>{marker.longitude}</td>
                                <td>{marker.address}</td>
                                <td>{marker.status}</td>
                                <td className="admin-table-actions">
                                    <button onClick={() => handleEdit(marker)}>수정</button>
                                    <button onClick={() => handleDelete(marker.id)}>삭제</button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

export default AdminPage;
