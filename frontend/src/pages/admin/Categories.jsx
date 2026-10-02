import { CategoryApi } from '../../services/api';
import SimpleCrud from './SimpleCrud';

const api = { ...CategoryApi, list: () => CategoryApi.list(true) };

export default function Categories() {
  return (
    <SimpleCrud title="Notice categories" api={api} empty={{ name: '', description: '', active: true }}
      fields={[{ key: 'name', label: 'Name', required: true }, { key: 'description', label: 'Description' }, { key: 'active', label: 'Active', type: 'checkbox' }]}
      columns={[{ key: 'name', label: 'Name' }, { key: 'description', label: 'Description' }, { key: 'active', label: 'Status', render: (c) => (c.active ? 'Active' : 'Inactive') }]} />
  );
}
