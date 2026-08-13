import dayjs from 'dayjs';
import {del, get, post, put} from './api';
import BackPainEpisode from '../model/BackPainEpisode';

function toCreatePayload(episode) {
    return {
        date: dayjs(episode.date).format('YYYY-MM-DD'),
        region: episode.region,
        side: episode.side,
        severity: episode.severity,
        note: episode.note
    };
}

function toUpdatePayload(episode) {
    return {
        region: episode.region,
        side: episode.side,
        severity: episode.severity,
        note: episode.note
    };
}

function toBackPainEpisode(data) {
    return new BackPainEpisode(data);
}

export default {
    async get_all() {
        return (await get('/back-pain-episodes')).map(toBackPainEpisode);
    },
    async save(episode) {
        const data = episode.id
            ? await put(`/back-pain-episodes/${episode.id}`, toUpdatePayload(episode))
            : await post('/back-pain-episodes', toCreatePayload(episode));
        return toBackPainEpisode(data);
    },
    delete(episode) {
        return del(`/back-pain-episodes/${episode.id}`);
    }
};
