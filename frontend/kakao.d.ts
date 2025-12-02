declare namespace kakao.maps {
    class LatLng {
        constructor(lat: number, lng: number);
    }

    class Map {
        constructor(container: HTMLElement, options: MapOptions);
        setCenter(latlng: LatLng): void;
        setLevel(level: number): void;
    }

    interface MapOptions {
        center: LatLng;
        level: number;
    }

    class Marker {
        constructor(options: MarkerOptions);
        setMap(map: Map | null): void;
    }

    interface MarkerOptions {
        position: LatLng;
        map?: Map;
    }

    class InfoWindow {
        constructor(options: InfoWindowOptions);
        open(map: Map, marker: Marker): void;
    }

    interface InfoWindowOptions {
        content: string;
    }

    namespace event {
        function addListener(target: any, type: string, callback: () => void): void;
    }

    function load(callback: () => void): void;
}
