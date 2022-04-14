import { createWebHistory, createRouter } from "vue-router";
import Home from "@/components/Home.vue";
import WeightHistory from "@/components/WeightHistory";
import HabitList from "@/components/HabitList";
import RoutineList from "@/components/RoutineList";
import BloodPressureHistory from "@/components/BloodPressureHistory";
import Backup from "@/components/Backup.vue";
import Login from "@/components/Login.vue";

const routes = [
    {
        path: "/",
        name: "Home",
        component: Home,
    },
    {
        path: "/weights",
        name: "WeightHistory",
        component: WeightHistory,
    },
    {
        path: "/pressures",
        name: "BloodPressureHistory",
        component: BloodPressureHistory,
    },
    {
        path: "/habits",
        name: "HabitList",
        component: HabitList,
    },
    {
        path: "/routines",
        name: "RoutineList",
        component: RoutineList,
    },
    {
        path: "/backup",
        name: "Backup",
        component: Backup,
    },
    {
        path: "/login",
        name: "Login",
        component: Login,
    }
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

export default router;
